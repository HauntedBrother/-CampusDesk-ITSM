package edu.university.itsm.incident;

import edu.university.itsm.identity.User;
import edu.university.itsm.incident.automation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.*;
import static edu.university.itsm.incident.IncidentStatus.*;

@Service @Transactional
public class IncidentAutomation {
    public static final List<IncidentStatus> OPEN=List.of(NEW,CLASSIFIED,ASSIGNED,ACKNOWLEDGED,IN_PROGRESS);
    private final AutomationLock lock;
    private final PriorityPolicy priority;
    private final CalendarSlaPolicy sla;
    private final AssignmentEngine assignments;
    private final IncidentAudit history;
    private final IncidentRepository incidents;
    private final Clock clock;
    public IncidentAutomation(AutomationLock lock,PriorityPolicy priority,CalendarSlaPolicy sla,AssignmentEngine assignments,
        IncidentAudit history,IncidentRepository incidents,Clock clock) {
        this.lock=lock; this.priority=priority; this.sla=sla; this.assignments=assignments; this.history=history; this.incidents=incidents; this.clock=clock;
    }
    public AutomationSettings lock(){return lock.acquire();}
    public void refresh(Incident incident,User actor,boolean tryAssignment) {
        AutomationSettings settings=lock.acquire();
        SlaSnapshot snapshot=incident.getSla(); snapshot.syncMilestones(incident.getAcknowledgedAt(),incident.getResolvedAt());
        evaluate(incident,actor);
        if(OPEN.contains(incident.getStatus()) || incident.getPriority()==null) {
            int criticality=incident.getService().getBusinessCriticality(); int score=incident.getImpact()*incident.getUrgency()*criticality;
            IncidentPriority calculated=priority.calculate(incident.getImpact(),incident.getUrgency(),criticality);
            if(incident.getPriority()!=calculated || !Objects.equals(incident.getPriorityScore(),score) || !Objects.equals(incident.getPriorityCriticality(),criticality)) {
                Map<String,Object> oldValues=AuditSnapshot.of(incident);
                String before=incident.getPriority()+" (score="+incident.getPriorityScore()+")";
                incident.applyPriority(calculated,score,criticality);
                audit(incident,actor,"PRIORITY_CALCULATED",incident.getStatus(),before+" -> "+calculated+"; score="+incident.getImpact()+" × "+incident.getUrgency()+" × "+criticality+" = "+score,oldValues);
            }
        }
        // Completed incidents keep their last agreed targets. Reopening resumes the original clock.
        if(OPEN.contains(incident.getStatus()) || snapshot.getPolicyId()==null) {
            var policy=sla.applicable(incident.getService().getId(),incident.getPriority());
            if(policy.isPresent()) {
                Map<String,Object> oldValues=AuditSnapshot.of(incident);
                String before=snapshot.describe(); boolean recovered=snapshot.getIssue()!=null;
                if(snapshot.configure(policy.get(),incident.getCreatedAt()) || recovered)
                    audit(incident,actor,"SLA_RECALCULATED",incident.getStatus(),before+" -> "+snapshot.describe()+"; deadlines use original creation time",oldValues);
            } else {
                Map<String,Object> oldValues=AuditSnapshot.of(incident);
                String previous=snapshot.getIssue(); snapshot.missingPolicy();
                if(!Objects.equals(previous,snapshot.getIssue())) audit(incident,actor,"SLA_POLICY_MISSING",incident.getStatus(),snapshot.getIssue(),oldValues);
            }
        }
        evaluate(incident,actor);
        if(tryAssignment && (incident.getStatus()==NEW || incident.getStatus()==CLASSIFIED)) {
            if(settings.isAutomaticAssignment()) assignAutomatically(incident,actor,settings.getStrategy());
            else assignmentIssue(incident,actor,"Automatic assignment is disabled; an administrator must assign a qualified technician.");
        }
    }
    public void assignAutomatically(Incident incident,User actor,AssignmentMode strategy) {
        lock.acquire();
        if(incident.getStatus()==NEW) {
            Map<String,Object> oldValues=AuditSnapshot.of(incident);
            incident.transition(CLASSIFIED);
            audit(incident,actor,"CLASSIFICATION_CONFIRMED",NEW,"Confirmed the category selected in the report; classification follows the final selected category.",oldValues);
        }
        if(incident.getStatus()!=CLASSIFIED) throw new InvalidIncidentOperation("Automatic assignment requires a classified, unassigned incident");
        Optional<User> chosen=assignments.select(incident,strategy);
        if(chosen.isEmpty()) {
            assignmentIssue(incident,actor,"No available, qualified technician has remaining capacity for this category."); return;
        }
        Map<String,Object> oldValues=AuditSnapshot.of(incident);
        User technician=chosen.get(); incident.assign(technician); incident.assignmentIssue(null);
        audit(incident,actor,"AUTO_ASSIGNED",CLASSIFIED,"Strategy "+strategy+" assigned "+technician.getDisplayName()+" ("+technician.getId()+")",oldValues);
    }
    public User eligible(Incident incident,UUID technician){return assignments.requireEligible(incident,technician);}
    public void validateCategoryChange(Incident incident,UUID categoryId) {
        if(incident.getTechnician()!=null && !assignments.qualified(incident.getTechnician().getId(),categoryId))
            throw new InvalidIncidentOperation("Assigned technician lacks this category skill. Override to CLASSIFIED and reassign before changing category.");
    }
    public void manualAssignmentCompleted(Incident incident){incident.assignmentIssue(null);}
    public void refreshService(UUID serviceId,User actor) {
        lock.acquire();
        for(Long id:incidents.activeIdsForService(serviceId,OPEN)) incidents.lockById(id).ifPresent(incident -> refresh(incident,actor,false));
    }
    public void refreshOpen(User actor) {
        lock.acquire();
        for(Long id:incidents.idsInStatuses(OPEN)) incidents.lockById(id).ifPresent(incident -> refresh(incident,actor,false));
    }
    public void evaluate(Incident incident,User actor) {
        SlaSnapshot snapshot=incident.getSla(); snapshot.syncMilestones(incident.getAcknowledgedAt(),incident.getResolvedAt());
        Map<String,Object> oldValues=AuditSnapshot.of(incident);
        for(String event:snapshot.evaluate(clock.instant(),incident.getResolvedAt()))
            audit(incident,actor,event,incident.getStatus(),snapshot.describe()+"; observed="+clock.instant()+"; response completion="+snapshot.getFirstResponseAt()+"; resolution="+incident.getResolvedAt(),oldValues);
    }
    private void assignmentIssue(Incident incident,User actor,String issue) {
        if(!Objects.equals(incident.getAssignmentIssue(),issue)) {Map<String,Object> oldValues=AuditSnapshot.of(incident);incident.assignmentIssue(issue); audit(incident,actor,"ASSIGNMENT_PENDING",incident.getStatus(),issue,oldValues);}
    }
    private void audit(Incident incident,User actor,String action,IncidentStatus previous,String description,Map<String,Object> before) {
        incident.touch();
        history.record(incident,actor,action,previous,description,false,before);
    }
}
