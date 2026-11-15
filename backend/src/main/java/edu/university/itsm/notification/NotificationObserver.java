package edu.university.itsm.notification;
import org.slf4j.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import java.time.Instant;
import java.util.UUID;
@Component
public class NotificationObserver {
    private static final Logger LOG=LoggerFactory.getLogger(NotificationObserver.class);
    private final NotificationProcessor processor;
    private final NotificationOutboxRepository outbox;
    public NotificationObserver(NotificationProcessor processor,NotificationOutboxRepository outbox){this.processor=processor;this.outbox=outbox;}
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void afterCommit(NotificationEvents.Queued event){deliver(event.eventId());}
    public void deliver(UUID id) {
        try {processor.process(id);}
        catch(RuntimeException failure) {
            LOG.error("Notification event {} remains pending for retry",id,failure);
            try {processor.failed(id,failure.getClass().getSimpleName());} catch(RuntimeException unavailable){LOG.error("Cannot record notification retry for {}",id,unavailable);}
        }
    }
    @Scheduled(initialDelayString="${app.notifications.initial-delay-ms:15000}",fixedDelayString="${app.notifications.retry-delay-ms:15000}")
    public void retryPending(){for(UUID id:outbox.pending(Instant.now(),PageRequest.of(0,100))) deliver(id);}
}
