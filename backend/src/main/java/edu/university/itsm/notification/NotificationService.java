package edu.university.itsm.notification;
import edu.university.itsm.identity.*;
import edu.university.itsm.incident.IncidentPolicy;
import edu.university.itsm.shared.CurrentUser;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
@Service @Transactional(readOnly=true)
public class NotificationService {
    public record View(UUID id,Long incidentId,String reference,String action,String message,Instant createdAt,Instant readAt) {}
    public record Notifications(List<View> content,long totalElements,long unreadCount,int page,int totalPages) {}
    public record EmailView(UUID id,UUID eventId,Long incidentId,String email,String subject,String status,Instant deliveredAt) {}
    private final InAppNotificationRepository notifications;
    private final SimulatedEmailRepository emails;
    private final IncidentPolicy policy;
    public NotificationService(InAppNotificationRepository notifications,SimulatedEmailRepository emails,IncidentPolicy policy){this.notifications=notifications;this.emails=emails;this.policy=policy;}
    public Notifications list(User actor,boolean unreadOnly,int page,int size) {
        bounds(page,size);Specification<InAppNotification> scope=scope(actor);
        Specification<InAppNotification> unread=scope.and((r,q,c) -> c.isNull(r.get("readAt")));
        var found=notifications.findAll(unreadOnly ? unread : scope,PageRequest.of(page,size,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id"))));
        return new Notifications(found.getContent().stream().map(this::view).toList(),found.getTotalElements(),notifications.count(unread),page,found.getTotalPages());
    }
    @Transactional public View read(User actor,UUID id,boolean read) {
        InAppNotification n=notifications.lockById(id).filter(v -> v.getRecipient().getId().equals(actor.getId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Notification not found"));
        policy.requireRead(actor,n.getIncident());n.markRead(read);notifications.flush();return view(n);
    }
    public org.springframework.data.domain.Page<EmailView> emailLogs(User actor,int page,int size) {
        CurrentUser.requireRole(actor,Role.ADMIN);bounds(page,size);
        return emails.findAll(PageRequest.of(page,size,Sort.by(Sort.Order.desc("deliveredAt"),Sort.Order.desc("id"))))
            .map(e -> new EmailView(e.getId(),e.getEventId(),e.getIncidentId(),e.getEmail(),e.getSubject(),e.getStatus(),e.getDeliveredAt()));
    }
    private View view(InAppNotification n){return new View(n.getId(),n.getIncident().getId(),n.getIncident().getReference(),n.getAction(),n.getMessage(),n.getCreatedAt(),n.getReadAt());}
    private void bounds(int page,int size){if(page<0 || size<1 || size>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid pagination");}
    private Specification<InAppNotification> scope(User actor) {
        return (root,q,c) -> c.and(c.equal(root.get("recipient").get("id"),actor.getId()),switch(actor.getRole()) {
            case ADMIN -> c.conjunction();
            case EMPLOYEE -> c.equal(root.get("incident").get("reporter").get("id"),actor.getId());
            case TECHNICIAN -> c.equal(root.get("incident").get("technician").get("id"),actor.getId());
        });
    }
}
