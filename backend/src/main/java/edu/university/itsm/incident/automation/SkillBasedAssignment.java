package edu.university.itsm.incident.automation;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class SkillBasedAssignment implements AssignmentStrategy {
    public AssignmentMode mode(){return AssignmentMode.SKILL_BASED;}
    public Optional<Candidate> select(List<Candidate> candidates) {
        return candidates.stream().filter(Candidate::eligible).min(Comparator.comparingInt(Candidate::proficiency).reversed()
            .thenComparingLong(Candidate::workload).thenComparing(c -> c.userId().toString()));
    }
}
