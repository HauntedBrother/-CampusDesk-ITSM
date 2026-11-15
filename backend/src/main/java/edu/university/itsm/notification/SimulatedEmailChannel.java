package edu.university.itsm.notification;
import edu.university.itsm.identity.User;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
@Component @Order(1)
public class SimulatedEmailChannel implements NotificationChannel {
    private final SimulatedEmailRepository deliveries;
    public SimulatedEmailChannel(SimulatedEmailRepository deliveries){this.deliveries=deliveries;}
    public void deliver(NotificationOutbox event,User recipient){if(!deliveries.existsByEventIdAndRecipientId(event.getId(),recipient.getId())) deliveries.save(new SimulatedEmailDelivery(event,recipient));}
}
