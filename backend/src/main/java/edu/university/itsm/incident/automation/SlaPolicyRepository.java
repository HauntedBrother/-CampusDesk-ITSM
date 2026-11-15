package edu.university.itsm.incident.automation;
import edu.university.itsm.incident.IncidentPriority;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SlaPolicyRepository extends JpaRepository<SlaPolicyEntity,UUID> {
    Optional<SlaPolicyEntity> findByServiceIdAndPriorityAndActiveTrue(UUID serviceId,IncidentPriority priority);
    Optional<SlaPolicyEntity> findByServiceIdIsNullAndPriorityAndActiveTrue(IncidentPriority priority);
    Optional<SlaPolicyEntity> findByServiceIdAndPriority(UUID serviceId,IncidentPriority priority);
    Optional<SlaPolicyEntity> findByServiceIdIsNullAndPriority(IncidentPriority priority);
}
