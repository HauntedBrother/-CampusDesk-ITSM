package edu.university.itsm.incident.automation;

import edu.university.itsm.identity.*;
import edu.university.itsm.incident.*;
import edu.university.itsm.catalog.*;
import edu.university.itsm.shared.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @Transactional(readOnly=true)
public class AutomationConfigurationService {
    private final AutomationSettingsRepository settings;
    private final SlaPolicyRepository policies;
    private final TechnicianProfileRepository profiles;
    private final UserRepository users;
    private final ITServiceRepository services;
    private final IncidentCategoryRepository categories;
    private final ConfigurationAuditRepository audit;
    private final IncidentAutomation automation;
    private final AssignmentEngine assignments;
    private final IncidentRepository incidents;
    public AutomationConfigurationService(AutomationSettingsRepository settings,SlaPolicyRepository policies,TechnicianProfileRepository profiles,UserRepository users,
        ITServiceRepository services,IncidentCategoryRepository categories,ConfigurationAuditRepository audit,IncidentAutomation automation,AssignmentEngine assignments,IncidentRepository incidents) {
        this.settings=settings; this.policies=policies; this.profiles=profiles; this.users=users; this.services=services; this.categories=categories;
        this.audit=audit; this.automation=automation; this.assignments=assignments; this.incidents=incidents;
    }
    public AutomationDtos.Settings settings(User actor) {
        CurrentUser.requireRole(actor,Role.ADMIN); AutomationSettings s=settings.findById(1).orElseThrow(() -> new InvalidIncidentOperation("Automation settings are missing"));
        return new AutomationDtos.Settings(s.getStrategy(),s.isAutomaticAssignment());
    }
    @Transactional public AutomationDtos.Settings configure(User actor,AutomationDtos.Settings input) {
        CurrentUser.requireRole(actor,Role.ADMIN); AutomationSettings s=automation.lock();
        String before=s.getStrategy()+", automatic="+s.isAutomaticAssignment(); s.configure(input.strategy(),input.automaticAssignment());
        audit.save(new ConfigurationAudit(actor,"ASSIGNMENT_SETTINGS",before+" -> "+input));
        return new AutomationDtos.Settings(s.getStrategy(),s.isAutomaticAssignment());
    }
    public List<AutomationDtos.PolicyView> policies(User actor) {
        CurrentUser.requireRole(actor,Role.ADMIN); return policies.findAll().stream().sorted(Comparator.comparing(p -> (p.getServiceId()==null ? "" : p.getServiceId().toString())+p.getPriority())).map(AutomationDtos.PolicyView::of).toList();
    }
    @Transactional public AutomationDtos.PolicyView savePolicy(User actor,AutomationDtos.PolicyInput input) {
        CurrentUser.requireRole(actor,Role.ADMIN); automation.lock();
        if(input.resolutionSeconds()<input.responseSeconds()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Resolution target must not be shorter than response target");
        if(input.serviceId()!=null && !services.existsById(input.serviceId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Service not found");
        Optional<SlaPolicyEntity> existing=input.serviceId()==null ? policies.findByServiceIdIsNullAndPriority(input.priority()) : policies.findByServiceIdAndPriority(input.serviceId(),input.priority());
        String before=existing.map(p -> AutomationDtos.PolicyView.of(p).toString()).orElse("None");
        SlaPolicyEntity policy=existing.orElseGet(() -> new SlaPolicyEntity(input.serviceId(),input.priority(),input.responseSeconds(),input.resolutionSeconds(),input.warningPercent(),input.active()));
        policy.configure(input.responseSeconds(),input.resolutionSeconds(),input.warningPercent(),input.active()); policies.saveAndFlush(policy);
        audit.save(new ConfigurationAudit(actor,"SLA_POLICY",before+" -> "+AutomationDtos.PolicyView.of(policy)));
        automation.refreshOpen(actor);
        return AutomationDtos.PolicyView.of(policy);
    }
    public List<AutomationDtos.TechnicianView> technicians(User actor) {
        CurrentUser.requireRole(actor,Role.ADMIN);
        return users.findByRoleAndEnabledTrueOrderByDisplayNameAsc(Role.TECHNICIAN).stream().flatMap(u -> profiles.findById(u.getId()).stream().map(p -> view(u,p))).toList();
    }
    @Transactional public AutomationDtos.TechnicianView configureTechnician(User actor,UUID id,AutomationDtos.TechnicianInput input) {
        CurrentUser.requireRole(actor,Role.ADMIN); automation.lock();
        User user=users.findById(id).filter(u -> u.getRole()==Role.TECHNICIAN && u.isEnabled()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Technician not found"));
        TechnicianProfile profile=profiles.lockById(id).orElseThrow(() -> new InvalidIncidentOperation("Technician profile is missing"));
        Map<UUID,Integer> skills=new HashMap<>();
        for(var skill:input.skills()) {
            if(!categories.existsById(skill.categoryId()) || skills.put(skill.categoryId(),skill.proficiency())!=null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Skills must reference distinct existing categories");
        }
        if(input.capacity()<assignments.workload(id)) throw new InvalidIncidentOperation("Capacity cannot be reduced below the current active workload");
        for(Incident incident:incidents.findByTechnicianIdAndStatusIn(id,AssignmentEngine.ACTIVE))
            if(!skills.containsKey(incident.getCategory().getId())) throw new InvalidIncidentOperation("Cannot remove a skill required by an active assignment; reassign that work first");
        String before=view(user,profile).toString(); profile.configure(input.capacity(),input.available(),skills); profiles.flush();
        audit.save(new ConfigurationAudit(actor,"TECHNICIAN_CONFIGURATION",before+" -> "+view(user,profile)));
        return view(user,profile);
    }
    public List<AutomationDtos.AuditView> history(User actor) {
        CurrentUser.requireRole(actor,Role.ADMIN); return audit.findTop100ByOrderByIdDesc().stream()
            .map(a -> new AutomationDtos.AuditView(a.getId(),a.getActor().getDisplayName(),a.getAction(),a.getDescription(),a.getCreatedAt())).toList();
    }
    private AutomationDtos.TechnicianView view(User u,TechnicianProfile p) {
        return new AutomationDtos.TechnicianView(u.getId(),u.getDisplayName(),p.getCapacity(),p.isAvailable(),assignments.workload(u.getId()),
            p.getSkills().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> new AutomationDtos.Skill(e.getKey(),e.getValue())).toList());
    }
}
