package edu.university.itsm.notification;
import edu.university.itsm.incident.Incident;
import edu.university.itsm.identity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="in_app_notifications")
public class InAppNotification {
    @Id private UUID id;
    private UUID eventId;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="incident_id") private Incident incident;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="recipient_id") private User recipient;
    @Column(length=40,nullable=false) private String action;
    @Column(length=500,nullable=false) private String message;
    private Instant createdAt;
    private Instant readAt;
    protected InAppNotification() {}
    public InAppNotification(NotificationOutbox event,User user){id=UUID.randomUUID();eventId=event.getId();incident=event.getIncident();recipient=user;action=event.getAction();message=event.getMessage();createdAt=Instant.now();}
    public void markRead(boolean read){if(read && readAt==null) readAt=Instant.now();if(!read) readAt=null;}
    public UUID getId(){return id;}
    public UUID getEventId(){return eventId;}
    public Incident getIncident(){return incident;}
    public User getRecipient(){return recipient;}
    public String getAction(){return action;}
    public String getMessage(){return message;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getReadAt(){return readAt;}
}
