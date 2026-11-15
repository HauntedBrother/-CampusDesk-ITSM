package edu.university.itsm.incident.automation;
import java.util.*;
/** Pure selection policy over eligible candidates; the engine rechecks eligibility under locks. */
public interface AssignmentStrategy {
    record Candidate(UUID userId,int proficiency,long workload,int capacity,boolean available) {
        public boolean eligible(){return available && proficiency>0 && workload<capacity;}
    }
    AssignmentMode mode();
    Optional<Candidate> select(List<Candidate> candidates);
}
