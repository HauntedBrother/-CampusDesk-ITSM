package edu.university.itsm.notification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface InAppNotificationRepository extends JpaRepository<InAppNotification,UUID>,JpaSpecificationExecutor<InAppNotification> {
    boolean existsByEventIdAndRecipientId(UUID eventId,UUID recipientId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select n from InAppNotification n where n.id=:id")
    Optional<InAppNotification> lockById(@Param("id") UUID id);
}
