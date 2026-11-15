package edu.university.itsm.incident.automation;
import edu.university.itsm.incident.IncidentPriority;
import org.springframework.stereotype.Component;
@Component
public class MultiplicativePriorityPolicy implements PriorityPolicy {
    public int score(int impact,int urgency,int criticality) {
        if(impact<1 || impact>3 || urgency<1 || urgency>3 || criticality<1 || criticality>3) throw new IllegalArgumentException("Priority factors must be between 1 and 3");
        return impact*urgency*criticality;
    }
    public IncidentPriority calculate(int impact,int urgency,int criticality) { return fromScore(score(impact,urgency,criticality)); }
    public IncidentPriority fromScore(int score) {
        if(score<1 || score>27) throw new IllegalArgumentException("Priority score must be between 1 and 27");
        return score>=18 ? IncidentPriority.P1 : score>=9 ? IncidentPriority.P2 : score>=4 ? IncidentPriority.P3 : IncidentPriority.P4;
    }
}
