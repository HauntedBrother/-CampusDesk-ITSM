package edu.university.itsm.notification;
import edu.university.itsm.identity.User;
/** Observer contract: delivery participates in the outbox processor's new transaction. */
public interface NotificationChannel {void deliver(NotificationOutbox event,User recipient);}
