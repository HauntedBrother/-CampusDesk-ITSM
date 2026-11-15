package edu.university.itsm.notification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from NotificationOutbox e where e.id=:id")
    Optional<NotificationOutbox> lockById(@Param("id") UUID id);
    @Query("select e.id from NotificationOutbox e where e.processedAt is null and e.availableAt<=:now order by e.createdAt,e.id")
    List<UUID> pending(@Param("now") Instant now,org.springframework.data.domain.Pageable page);
}
