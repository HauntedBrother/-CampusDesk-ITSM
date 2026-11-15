package edu.university.itsm.incident;

/** State objects own valid forward transitions. The application service separately enforces actor permissions. */
public interface IncidentState {
    IncidentStatus status();
    IncidentStatus next();
    default void requireTransition(IncidentStatus target) {
        if (target==null || target!=next()) throw new InvalidIncidentOperation("Cannot move from " + status() + " to " + target);
    }
}
