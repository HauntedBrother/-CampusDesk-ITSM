package edu.university.itsm.incident;

import edu.university.itsm.catalog.*;
import edu.university.itsm.identity.*;
import edu.university.itsm.shared.CurrentUser;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static edu.university.itsm.incident.IncidentStatus.*;

@Service @Transactional(readOnly=true)
public class IncidentService {
    private final IncidentRepository incidents;
    private final IncidentCommentRepository comments;
    private final IncidentHistoryRepository history;
    private final ITServiceRepository services;
    private final IncidentCategoryRepository categories;
    private final IncidentPolicy policy;
    private final IncidentAutomation automation;
    private final IncidentAudit auditWriter;
    private final edu.university.itsm.intelligence.IntelligenceService intelligence;
    public IncidentService(IncidentRepository incidents,IncidentCommentRepository comments,IncidentHistoryRepository history,
        ITServiceRepository services,IncidentCategoryRepository categories,IncidentPolicy policy,IncidentAutomation automation,IncidentAudit auditWriter,edu.university.itsm.intelligence.IntelligenceService intelligence) {
        this.incidents=incidents; this.comments=comments; this.history=history; this.services=services; this.categories=categories;
        this.policy=policy;
        this.automation=automation; this.auditWriter=auditWriter; this.intelligence=intelligence;
    }
    public IncidentDtos.Page search(User actor,IncidentDtos.Search search,int page,int size) {
        return search(actor,search,page,size,"createdAt","desc");
    }
    public IncidentDtos.Page search(User actor,IncidentDtos.Search search,int page,int size,String sort,String direction) {
        if(page<0 || size<1 || size>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Page must be nonnegative and size between 1 and 100");
        Page<Incident> result=incidents.findAll(IncidentSpecifications.visibleTo(actor,search),PageRequest.of(page,size,IncidentSorting.sort(sort,direction)));
        return new IncidentDtos.Page(result.getContent().stream().map(IncidentDtos.View::of).toList(),result.getTotalElements(),result.getTotalPages(),page,size);
    }
    public IncidentDtos.Detail get(User actor,Long id) {
        Incident incident=incidents.findById(id).orElseThrow(IncidentService::missing);
        policy.requireRead(actor,incident); return detail(actor,incident);
    }
    @Transactional public IncidentDtos.Detail create(User actor,IncidentDtos.Input input) {
        CurrentUser.requireRole(actor,Role.EMPLOYEE);
        automation.lock();
        ITService service=activeService(input.serviceId(),null);
        UUID categoryId=input.categoryId()==null ? intelligence.resolveCategory(null,input.title(),input.description()) : input.categoryId();
        IncidentCategory category=activeCategory(categoryId,null);
        Incident incident=incidents.saveAndFlush(new Incident(actor,input.title(),input.description(),service,category,input.impact(),input.urgency()));
        Map<String,Object> createdValues=AuditSnapshot.of(incident); createdValues.put("categorySource",input.categoryId()==null ? "RULE" : "USER");
        auditWriter.record(incident,actor,"CREATED",null,"Incident reported. "+snapshot(incident),false,Map.of(),createdValues);
        automation.refresh(incident,actor,true); incidents.flush();
        return detail(actor,incident);
    }
    @Transactional public IncidentDtos.Detail update(User actor,Long id,IncidentDtos.Input input) {
        Incident incident=locked(actor,id); policy.requireAction(actor,incident,"UPDATE");
        UUID categoryId=input.categoryId()==null ? intelligence.resolveCategory(null,input.title(),input.description()) : input.categoryId();
        if(unchanged(incident,input,categoryId)) return detail(actor,incident);
        String before=snapshot(incident); Map<String,Object> oldValues=AuditSnapshot.of(incident);
        automation.validateCategoryChange(incident,categoryId);
        incident.update(input.title(),input.description(),activeService(input.serviceId(),incident.getService().getId()),activeCategory(categoryId,incident.getCategory().getId()),input.impact(),input.urgency());
        audit(incident,actor,"UPDATED",incident.getStatus(),"Before: "+before+"\nAfter: "+snapshot(incident),false,oldValues);
        automation.refresh(incident,actor,true);
        incidents.flush(); return detail(actor,incident);
    }
    @Transactional public IncidentDtos.Detail classify(User actor,Long id) { return advance(actor,id,"CLASSIFY",CLASSIFIED); }
    @Transactional public IncidentDtos.Detail acknowledge(User actor,Long id) { return advance(actor,id,"ACKNOWLEDGE",ACKNOWLEDGED); }
    @Transactional public IncidentDtos.Detail start(User actor,Long id) { return advance(actor,id,"START",IN_PROGRESS); }
    @Transactional public IncidentDtos.Detail verify(User actor,Long id) { return advance(actor,id,"VERIFY",VERIFIED); }
    @Transactional public IncidentDtos.Detail close(User actor,Long id) { return advance(actor,id,"CLOSE",CLOSED); }
    private IncidentDtos.Detail advance(User actor,Long id,String action,IncidentStatus target) {
        Incident incident=locked(actor,id); policy.requireAction(actor,incident,action);
        IncidentStatus previous=incident.getStatus(); Map<String,Object> oldValues=AuditSnapshot.of(incident); incident.transition(target);
        audit(incident,actor,action,previous,"Moved from "+previous+" to "+target,false,oldValues);
        automation.refresh(incident,actor,target==CLASSIFIED);
        incidents.flush(); return detail(actor,incident);
    }
    @Transactional public IncidentDtos.Detail assign(User actor,Long id,IncidentDtos.Assignment input) {
        CurrentUser.requireRole(actor,Role.ADMIN);
        Incident incident=locked(actor,id); policy.requireAction(actor,incident,"ASSIGN");
        if(incident.getTechnician()!=null && incident.getTechnician().getId().equals(input.technicianId())) return detail(actor,incident);
        Map<String,Object> oldValues=AuditSnapshot.of(incident);
        User technician=automation.eligible(incident,input.technicianId());
        IncidentStatus previous=incident.getStatus();
        String previousName=incident.getTechnician()==null ? "Unassigned" : incident.getTechnician().getDisplayName()+" ("+incident.getTechnician().getId()+")";
        incident.assign(technician);
        automation.manualAssignmentCompleted(incident);
        audit(incident,actor,"ASSIGNED",previous,previousName+" -> "+technician.getDisplayName()+" ("+technician.getId()+"); manual override"+(input.reason()==null ? "" : "; reason: "+input.reason().strip()),false,oldValues);
        automation.refresh(incident,actor,false);
        incidents.flush(); return detail(actor,incident);
    }
    @Transactional public IncidentDtos.Detail resolve(User actor,Long id,IncidentDtos.Resolution input) {
        Incident incident=locked(actor,id); policy.requireAction(actor,incident,"RESOLVE");
        IncidentStatus previous=incident.getStatus(); Map<String,Object> oldValues=AuditSnapshot.of(incident); incident.resolve(input.resolution());
        audit(incident,actor,"RESOLVED",previous,"Resolution: "+incident.getResolution(),false,oldValues);
        automation.refresh(incident,actor,false);
        incidents.flush(); return detail(actor,incident);
    }
    @Transactional public IncidentDtos.Detail comment(User actor,Long id,IncidentDtos.Comment input) {
        Incident incident=locked(actor,id); policy.requireAction(actor,incident,"COMMENT");
        if(input.internal() && actor.getRole()==Role.EMPLOYEE) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Employees cannot add internal notes");
        Map<String,Object> oldValues=AuditSnapshot.of(incident);
        comments.saveAndFlush(new IncidentComment(incident,actor,input.body(),input.internal()));
        incident.touch();
        Map<String,Object> newValues=AuditSnapshot.of(incident);newValues.put("commentBody",input.body().strip());
        auditWriter.record(incident,actor,input.internal() ? "NOTE_ADDED" : "COMMENT_ADDED",incident.getStatus(),input.internal() ? "Internal investigation note added" : "Public comment added",input.internal(),oldValues,newValues);
        incidents.flush(); return detail(actor,incident);
    }
    @Transactional public IncidentDtos.Detail feedback(User actor,Long id,IncidentDtos.Feedback input) {
        Incident incident=locked(actor,id); policy.requireAction(actor,incident,"FEEDBACK");
        if(Objects.equals(incident.getFeedbackRating(),input.rating()) && Objects.equals(incident.getFeedback(),input.feedback().strip())) return detail(actor,incident);
        Map<String,Object> oldValues=AuditSnapshot.of(incident);
        String before=incident.getFeedbackRating()==null ? "None" : incident.getFeedbackRating()+"/5: "+incident.getFeedback();
        incident.provideFeedback(input.rating(),input.feedback());
        audit(incident,actor,"FEEDBACK",incident.getStatus(),"Before: "+before+"\nAfter: "+input.rating()+"/5: "+input.feedback().strip(),false,oldValues);
        incidents.flush(); return detail(actor,incident);
    }
    @Transactional public IncidentDtos.Detail override(User actor,Long id,IncidentDtos.Override input) {
        CurrentUser.requireRole(actor,Role.ADMIN);
        Incident incident=locked(actor,id);
        // Reopening a resolved incident consumes capacity again; serialize with normal assignment.
        if(incident.getTechnician()!=null && input.target().ordinal()>=ASSIGNED.ordinal() && input.target().ordinal()<RESOLVED.ordinal()
            && incident.getStatus().ordinal()>=RESOLVED.ordinal()) {
            automation.eligible(incident,incident.getTechnician().getId());
        }
        IncidentStatus previous=incident.getStatus();
        Map<String,Object> oldValues=AuditSnapshot.of(incident);
        String before="Resolution: "+incident.getResolution()+"; feedback: "+incident.getFeedbackRating()+"/5 "+incident.getFeedback();
        incident.overrideTo(input.target());
        audit(incident,actor,"ADMIN_OVERRIDE",previous,"Reason: "+input.reason().strip()+"\n"+previous+" -> "+incident.getStatus()+"\nPrevious details: "+before,false,oldValues);
        automation.refresh(incident,actor,false);
        incidents.flush(); return detail(actor,incident);
    }
    @Transactional public IncidentDtos.Detail autoAssign(User actor,Long id) {
        CurrentUser.requireRole(actor,Role.ADMIN);
        Incident incident=locked(actor,id);
        automation.assignAutomatically(incident,actor,automation.lock().getStrategy());
        automation.refresh(incident,actor,false); incidents.flush(); return detail(actor,incident);
    }
    private Incident locked(User actor,Long id) { automation.lock(); Incident incident=incidents.lockById(id).orElseThrow(IncidentService::missing); policy.requireRead(actor,incident); return incident; }
    private IncidentDtos.Detail detail(User actor,Incident incident) {
        boolean employee=actor.getRole()==Role.EMPLOYEE;
        var visibleComments=employee ? comments.findByIncidentIdAndInternalFalseOrderByIdAsc(incident.getId()) : comments.findByIncidentIdOrderByIdAsc(incident.getId());
        var visibleHistory=employee ? history.findByIncidentIdAndInternalFalseOrderByIdAsc(incident.getId()) : history.findByIncidentIdOrderByIdAsc(incident.getId());
        return new IncidentDtos.Detail(IncidentDtos.View.of(incident),visibleComments.stream().map(IncidentDtos.CommentView::of).toList(),visibleHistory.stream().map(IncidentDtos.HistoryView::of).toList(),policy.actions(actor,incident));
    }
    private void audit(Incident incident,User actor,String action,IncidentStatus previous,String description,boolean internal,Map<String,Object> before) {
        auditWriter.record(incident,actor,action,previous,description,internal,before);
    }
    private ITService activeService(UUID id,UUID existing) {
        return services.findById(id).filter(s -> s.isActive() || s.getId().equals(existing))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,"Select an active IT service"));
    }
    private IncidentCategory activeCategory(UUID id,UUID existing) {
        return categories.findById(id).filter(c -> c.isActive() || c.getId().equals(existing))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,"Select an active incident category"));
    }
    private boolean unchanged(Incident i,IncidentDtos.Input v,UUID categoryId) {
        return i.getTitle().equals(v.title().strip()) && i.getDescription().equals(v.description().strip()) && i.getService().getId().equals(v.serviceId()) && i.getCategory().getId().equals(categoryId) && i.getImpact()==v.impact() && i.getUrgency()==v.urgency();
    }
    private String snapshot(Incident i) {
        return "Title: "+i.getTitle()+"; description: "+i.getDescription()+"; service: "+i.getService().getName()+" ("+i.getService().getId()+"); category: "+i.getCategory().getName()+" ("+i.getCategory().getId()+"); impact: "+i.getImpact()+"; urgency: "+i.getUrgency();
    }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Incident not found"); }
}
