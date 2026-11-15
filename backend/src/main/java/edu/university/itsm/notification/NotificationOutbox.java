package edu.university.itsm.notification;
import edu.university.itsm.incident.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="notification_outbox")
public class NotificationOutbox {
    @Id private UUID id;
    @Column(nullable=false,unique=true) private Long historyId;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="incident_id") private Incident incident;
    @Column(length=40,nullable=false) private String action;
    @Column(length=500,nullable=false) private String message;
    private Instant createdAt;
    private Instant availableAt;
    private Instant processedAt;
    private int attempts;
    @Column(length=500) private String lastError;
    @ElementCollection @CollectionTable(name="notification_outbox_recipients",joinColumns=@JoinColumn(name="event_id"))
    @Column(name="user_id",nullable=false) private Set<UUID> recipients=new HashSet<>();
    protected NotificationOutbox() {}
    public NotificationOutbox(IncidentHistory history,Incident incident,Set<UUID> recipients) {
        id=UUID.randomUUID(); historyId=history.getId(); this.incident=incident; action=history.getAction();
        message=incident.getReference()+": "+action.toLowerCase(Locale.ROOT).replace('_',' ');
        createdAt=Instant.now(); availableAt=createdAt; this.recipients.addAll(recipients);
    }
    public void delivered(){processedAt=Instant.now();lastError=null;}
    public void failed(String error){attempts++;lastError=error==null ? "Delivery failed" : error.substring(0,Math.min(error.length(),500));availableAt=Instant.now().plusSeconds(Math.min(300,5L*(1L<<Math.min(attempts,6))));}
    public UUID getId(){return id;}
    public Long getHistoryId(){return historyId;}
    public Incident getIncident(){return incident;}
    public String getAction(){return action;}
    public String getMessage(){return message;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getProcessedAt(){return processedAt;}
    public Set<UUID> getRecipients(){return Set.copyOf(recipients);}
    public int getAttempts(){return attempts;}
}
