package edu.university.itsm.incident;
import java.util.Map;
import static edu.university.itsm.incident.IncidentStatus.*;

public final class IncidentStates {
    private IncidentStates() {}
    private static final Map<IncidentStatus,IncidentState> STATES=Map.of(
        NEW,new New(),CLASSIFIED,new Classified(),ASSIGNED,new Assigned(),ACKNOWLEDGED,new Acknowledged(),
        IN_PROGRESS,new InProgress(),RESOLVED,new Resolved(),VERIFIED,new Verified(),CLOSED,new Closed());
    public static IncidentState of(IncidentStatus status) { return STATES.get(status); }
    private record New() implements IncidentState { public IncidentStatus status(){return NEW;} public IncidentStatus next(){return CLASSIFIED;} }
    private record Classified() implements IncidentState { public IncidentStatus status(){return CLASSIFIED;} public IncidentStatus next(){return ASSIGNED;} }
    private record Assigned() implements IncidentState { public IncidentStatus status(){return ASSIGNED;} public IncidentStatus next(){return ACKNOWLEDGED;} }
    private record Acknowledged() implements IncidentState { public IncidentStatus status(){return ACKNOWLEDGED;} public IncidentStatus next(){return IN_PROGRESS;} }
    private record InProgress() implements IncidentState { public IncidentStatus status(){return IN_PROGRESS;} public IncidentStatus next(){return RESOLVED;} }
    private record Resolved() implements IncidentState { public IncidentStatus status(){return RESOLVED;} public IncidentStatus next(){return VERIFIED;} }
    private record Verified() implements IncidentState { public IncidentStatus status(){return VERIFIED;} public IncidentStatus next(){return CLOSED;} }
    private record Closed() implements IncidentState { public IncidentStatus status(){return CLOSED;} public IncidentStatus next(){return null;} }
}
