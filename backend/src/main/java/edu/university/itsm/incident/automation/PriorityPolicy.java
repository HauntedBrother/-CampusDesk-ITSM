package edu.university.itsm.incident.automation;
import edu.university.itsm.incident.IncidentPriority;
/** Priority algorithms remain interchangeable through this contract. */
public interface PriorityPolicy {
    IncidentPriority calculate(int impact,int urgency,int businessCriticality);
}
