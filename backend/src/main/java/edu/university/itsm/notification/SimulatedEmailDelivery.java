package edu.university.itsm.notification;
import edu.university.itsm.identity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="simulated_email_deliveries")
public class SimulatedEmailDelivery {
    @Id private UUID id;
    private UUID eventId;
    private Long incidentId;
    private UUID recipientId;
    @Column(length=254,nullable=false) private String email;
    @Column(length=500,nullable=false) private String subject;
    @Column(length=20,nullable=false) private String status="SIMULATED";
    private Instant deliveredAt;
    protected SimulatedEmailDelivery() {}
    public SimulatedEmailDelivery(NotificationOutbox event,User recipient){id=UUID.randomUUID();eventId=event.getId();incidentId=event.getIncident().getId();recipientId=recipient.getId();email=recipient.getEmail();subject=event.getMessage();deliveredAt=Instant.now();}
    public UUID getId(){return id;}
    public UUID getEventId(){return eventId;}
    public Long getIncidentId(){return incidentId;}
    public String getEmail(){return email;}
    public String getSubject(){return subject;}
    public String getStatus(){return status;}
    public Instant getDeliveredAt(){return deliveredAt;}
}
