package edu.university.itsm.incident;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface IncidentHistoryRepository extends JpaRepository<IncidentHistory,Long> {
    @org.springframework.data.jpa.repository.Query("select h from IncidentHistory h where h.incident.id=:incidentId order by h.id")
    List<IncidentHistory> findByIncidentIdOrderByIdAsc(Long incidentId);
    @org.springframework.data.jpa.repository.Query("select h from IncidentHistory h where h.incident.id=:incidentId and h.internal=false order by h.id")
    List<IncidentHistory> findByIncidentIdAndInternalFalseOrderByIdAsc(Long incidentId);
}
