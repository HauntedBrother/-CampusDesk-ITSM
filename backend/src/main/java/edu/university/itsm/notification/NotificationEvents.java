package edu.university.itsm.notification;
import edu.university.itsm.incident.*;
import edu.university.itsm.identity.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class NotificationEvents {
    public record Queued(UUID eventId) {}
    private static final Set<String> ACTIONS=Set.of("CREATED","ASSIGNED","AUTO_ASSIGNED","PRIORITY_CALCULATED","RESOLVED","CLOSE",
        "SLA_RESPONSE_APPROACHING","SLA_RESOLUTION_APPROACHING","SLA_RESPONSE_BREACHED","SLA_RESOLUTION_BREACHED");
    private final NotificationOutboxRepository outbox;
    private final UserRepository users;
    private final ApplicationEventPublisher events;
    public NotificationEvents(NotificationOutboxRepository outbox,UserRepository users,ApplicationEventPublisher events){this.outbox=outbox;this.users=users;this.events=events;}
    public void queue(IncidentHistory history,Incident incident) {
        boolean override=history.getAction().equals("ADMIN_OVERRIDE") && (incident.getStatus()==IncidentStatus.CLOSED || incident.getStatus()==IncidentStatus.RESOLVED);
        if(history.isInternal() || (!ACTIONS.contains(history.getAction()) && !override)) return;
        Set<UUID> recipients=new HashSet<>();recipients.add(incident.getReporter().getId());
        if(incident.getTechnician()!=null) recipients.add(incident.getTechnician().getId());
        users.findByRoleAndEnabledTrueOrderByDisplayNameAsc(Role.ADMIN).forEach(u -> recipients.add(u.getId()));
        NotificationOutbox event=outbox.saveAndFlush(new NotificationOutbox(history,incident,recipients));
        events.publishEvent(new Queued(event.getId()));
    }
}
