package edu.university.itsm.incident;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface IncidentRepository extends JpaRepository<Incident,Long>,JpaSpecificationExecutor<Incident> {
    @Override
    @EntityGraph(attributePaths={"reporter","service","category","technician"})
    org.springframework.data.domain.Page<Incident> findAll(org.springframework.data.jpa.domain.Specification<Incident> specification,org.springframework.data.domain.Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Incident i where i.id = :id")
    Optional<Incident> lockById(@Param("id") Long id);
    long countByTechnicianIdAndStatusIn(UUID technicianId, Collection<IncidentStatus> statuses);
    List<Incident> findByTechnicianIdAndStatusIn(UUID technicianId,Collection<IncidentStatus> statuses);
    @Query("select i.id from Incident i where (i.status in :statuses or i.sla.slaEvaluatedAt is null) and i.id > :after order by i.id")
    List<Long> scanIds(@Param("statuses") Collection<IncidentStatus> statuses,@Param("after") long after,org.springframework.data.domain.Pageable page);
    @Query("select i.id from Incident i where i.service.id = :serviceId and i.status in :statuses order by i.id")
    List<Long> activeIdsForService(@Param("serviceId") UUID serviceId,@Param("statuses") Collection<IncidentStatus> statuses);
    @Query("select i.id from Incident i where i.status in :statuses order by i.id")
    List<Long> idsInStatuses(@Param("statuses") Collection<IncidentStatus> statuses);
}
