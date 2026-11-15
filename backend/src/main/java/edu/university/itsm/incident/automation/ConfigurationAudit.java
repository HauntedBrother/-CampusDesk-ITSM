package edu.university.itsm.incident.automation;
import edu.university.itsm.identity.User;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="automation_config_history")
public class ConfigurationAudit {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="actor_id") private User actor;
    @Column(length=40,nullable=false) private String action;
    @Column(columnDefinition="text",nullable=false) private String description;
    private Instant createdAt;
    protected ConfigurationAudit() {}
    public ConfigurationAudit(User actor,String action,String description){this.actor=actor; this.action=action; this.description=description; createdAt=Instant.now();}
    public Long getId(){return id;}
    public User getActor(){return actor;}
    public String getAction(){return action;}
    public String getDescription(){return description;}
    public Instant getCreatedAt(){return createdAt;}
}
