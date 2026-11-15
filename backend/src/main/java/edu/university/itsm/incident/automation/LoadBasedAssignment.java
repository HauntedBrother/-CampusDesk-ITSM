package edu.university.itsm.incident.automation;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class LoadBasedAssignment implements AssignmentStrategy {
    public AssignmentMode mode(){return AssignmentMode.LOAD_BASED;}
    public Optional<Candidate> select(List<Candidate> candidates) {
        return candidates.stream().filter(Candidate::eligible).min(Comparator.comparingLong(Candidate::workload)
            .thenComparing(Comparator.comparingInt(Candidate::proficiency).reversed()).thenComparing(c -> c.userId().toString()));
    }
}
