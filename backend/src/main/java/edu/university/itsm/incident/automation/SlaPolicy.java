package edu.university.itsm.incident.automation;
import edu.university.itsm.incident.IncidentPriority;
import java.time.Instant;
import java.util.UUID;
/** Calendar-time deadline calculation using administrator-configured policy records. */
public interface SlaPolicy {
    record Deadlines(Instant responseDueAt,Instant resolutionDueAt) {}
    Deadlines calculate(UUID serviceId,IncidentPriority priority,Instant createdAt);
}
