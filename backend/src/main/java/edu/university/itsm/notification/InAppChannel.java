package edu.university.itsm.notification;
import edu.university.itsm.identity.User;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
@Component @Order(0)
public class InAppChannel implements NotificationChannel {
    private final InAppNotificationRepository notifications;
    public InAppChannel(InAppNotificationRepository notifications){this.notifications=notifications;}
    public void deliver(NotificationOutbox event,User recipient){if(!notifications.existsByEventIdAndRecipientId(event.getId(),recipient.getId())) notifications.save(new InAppNotification(event,recipient));}
}
