package edu.university.itsm.incident;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface IncidentCommentRepository extends JpaRepository<IncidentComment,Long> {
    List<IncidentComment> findByIncidentIdOrderByIdAsc(Long incidentId);
    List<IncidentComment> findByIncidentIdAndInternalFalseOrderByIdAsc(Long incidentId);
}
