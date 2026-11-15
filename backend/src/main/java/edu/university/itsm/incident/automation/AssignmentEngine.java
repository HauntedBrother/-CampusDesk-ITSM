package edu.university.itsm.incident.automation;
import edu.university.itsm.identity.*;
import edu.university.itsm.incident.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AssignmentEngine {
    public static final List<IncidentStatus> ACTIVE=List.of(IncidentStatus.ASSIGNED,IncidentStatus.ACKNOWLEDGED,IncidentStatus.IN_PROGRESS);
    private final UserRepository users;
    private final TechnicianProfileRepository profiles;
    private final IncidentRepository incidents;
    private final Map<AssignmentMode,AssignmentStrategy> strategies;
    public AssignmentEngine(UserRepository users,TechnicianProfileRepository profiles,IncidentRepository incidents,List<AssignmentStrategy> strategies) {
        this.users=users; this.profiles=profiles; this.incidents=incidents;
        this.strategies=strategies.stream().collect(Collectors.toUnmodifiableMap(AssignmentStrategy::mode,Function.identity()));
    }
    public Optional<User> select(Incident incident,AssignmentMode mode) {
        AssignmentStrategy strategy=strategies.get(mode);
        if(strategy==null) throw new InvalidIncidentOperation("Selected assignment strategy is not configured");
        List<AssignmentStrategy.Candidate> candidates=new ArrayList<>();
        for(User user:users.findByRoleAndEnabledTrueOrderByDisplayNameAsc(Role.TECHNICIAN)) {
            profiles.findById(user.getId()).ifPresent(profile -> candidates.add(new AssignmentStrategy.Candidate(user.getId(),
                profile.proficiency(incident.getCategory().getId()),workload(user.getId()),profile.getCapacity(),profile.isAvailable())));
        }
        Optional<AssignmentStrategy.Candidate> chosen=strategy.select(candidates);
        if(chosen.isEmpty()) return Optional.empty();
        // Global automation lock serializes writers; profile lock also protects the selected configuration.
        return Optional.of(requireEligible(incident,chosen.get().userId()));
    }
    public User requireEligible(Incident incident,UUID userId) {
        User user=users.findById(userId).filter(u -> u.getRole()==Role.TECHNICIAN && u.isEnabled())
            .orElseThrow(() -> new InvalidIncidentOperation("Select an active technician"));
        TechnicianProfile profile=profiles.lockById(userId).orElseThrow(() -> new InvalidIncidentOperation("Technician profile is missing"));
        if(!profile.isAvailable()) throw new InvalidIncidentOperation("Technician is unavailable");
        if(profile.proficiency(incident.getCategory().getId())<1) throw new InvalidIncidentOperation("Technician is not qualified for this incident category");
        boolean alreadyActive=incident.getTechnician()!=null && incident.getTechnician().getId().equals(userId) && ACTIVE.contains(incident.getStatus());
        if(!alreadyActive && workload(userId)>=profile.getCapacity()) throw new InvalidIncidentOperation("Technician capacity has been reached");
        return user;
    }
    public boolean qualified(UUID userId,UUID categoryId) {return profiles.findById(userId).map(p -> p.proficiency(categoryId)>0).orElse(false);}
    public long workload(UUID userId){return incidents.countByTechnicianIdAndStatusIn(userId,ACTIVE);}
}
