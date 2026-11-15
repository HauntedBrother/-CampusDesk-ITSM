package edu.university.itsm.notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface SimulatedEmailRepository extends JpaRepository<SimulatedEmailDelivery,UUID> {
    boolean existsByEventIdAndRecipientId(UUID eventId,UUID recipientId);
}
