package edu.university.itsm.notification;
import edu.university.itsm.identity.*;
import edu.university.itsm.incident.IncidentPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
@Service
public class NotificationProcessor {
    private final NotificationOutboxRepository outbox;
    private final UserRepository users;
    private final IncidentPolicy policy;
    private final List<NotificationChannel> channels;
    public NotificationProcessor(NotificationOutboxRepository outbox,UserRepository users,IncidentPolicy policy,List<NotificationChannel> channels){this.outbox=outbox;this.users=users;this.policy=policy;this.channels=channels;}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void process(UUID id) {
        NotificationOutbox event=outbox.lockById(id).orElse(null);
        if(event==null || event.getProcessedAt()!=null) return;
        for(UUID userId:event.getRecipients()) users.findById(userId).filter(User::isEnabled).filter(u -> policy.canRead(u,event.getIncident()))
            .ifPresent(u -> channels.forEach(channel -> channel.deliver(event,u)));
        event.delivered();outbox.flush();
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void failed(UUID id,String reason){outbox.lockById(id).filter(e -> e.getProcessedAt()==null).ifPresent(e -> e.failed(reason));}
}
