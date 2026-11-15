package edu.university.itsm.incident;
import edu.university.itsm.identity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="incident_history")
public class IncidentHistory {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="incident_id") private Incident incident;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="actor_id") private User actor;
    @Column(length=40,nullable=false) private String action;
    @Enumerated(EnumType.STRING) @Column(length=20) private IncidentStatus previousStatus;
    @Enumerated(EnumType.STRING) @Column(length=20,nullable=false) private IncidentStatus newStatus;
    @Column(columnDefinition="text",nullable=false) private String description;
    private boolean internal;
    private Instant createdAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb",nullable=false) private Map<String,Object> oldValues=new LinkedHashMap<>();
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb",nullable=false) private Map<String,Object> newValues=new LinkedHashMap<>();
    protected IncidentHistory() {}
    public IncidentHistory(Incident incident,User actor,String action,IncidentStatus previous,String description,boolean internal) {
        this.incident=incident; this.actor=actor; this.action=action; previousStatus=previous; newStatus=incident.getStatus();
        this.description=description; this.internal=internal; createdAt=Instant.now();
    }
    public Long getId(){return id;}
    public Long getIncidentId(){return incident.getId();}
    public User getActor(){return actor;}
    public String getAction(){return action;}
    public IncidentStatus getPreviousStatus(){return previousStatus;}
    public IncidentStatus getNewStatus(){return newStatus;}
    public String getDescription(){return description;}
    public boolean isInternal(){return internal;}
    public Instant getCreatedAt(){return createdAt;}
    void values(Map<String,Object> oldValues,Map<String,Object> newValues){this.oldValues=new LinkedHashMap<>(oldValues);this.newValues=new LinkedHashMap<>(newValues);}
    public Map<String,Object> getOldValues(){return Collections.unmodifiableMap(oldValues);}
    public Map<String,Object> getNewValues(){return Collections.unmodifiableMap(newValues);}
}
