package edu.university.itsm.incident.automation;
import edu.university.itsm.incident.*;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;
@Component
public class CalendarSlaPolicy implements SlaPolicy {
    private final SlaPolicyRepository policies;
    public CalendarSlaPolicy(SlaPolicyRepository policies){this.policies=policies;}
    public Optional<SlaPolicyEntity> applicable(UUID serviceId,IncidentPriority priority) {
        return policies.findByServiceIdAndPriorityAndActiveTrue(serviceId,priority)
            .or(() -> policies.findByServiceIdIsNullAndPriorityAndActiveTrue(priority));
    }
    public Deadlines calculate(UUID serviceId,IncidentPriority priority,Instant createdAt) {
        SlaPolicyEntity policy=applicable(serviceId,priority).orElseThrow(() -> new InvalidIncidentOperation("No active SLA policy for "+priority));
        return new Deadlines(createdAt.plusSeconds(policy.getResponseSeconds()),createdAt.plusSeconds(policy.getResolutionSeconds()));
    }
}
