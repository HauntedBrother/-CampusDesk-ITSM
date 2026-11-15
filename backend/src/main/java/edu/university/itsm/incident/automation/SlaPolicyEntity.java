package edu.university.itsm.incident.automation;
import edu.university.itsm.incident.IncidentPriority;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="sla_policies")
public class SlaPolicyEntity {
    @Id private UUID id;
    private UUID serviceId;
    @Enumerated(EnumType.STRING) @Column(length=2,nullable=false) private IncidentPriority priority;
    private int responseSeconds;
    private int resolutionSeconds;
    private int warningPercent;
    private boolean active;
    protected SlaPolicyEntity() {}
    public SlaPolicyEntity(UUID serviceId,IncidentPriority priority,int response,int resolution,int warning,boolean active) {
        id=UUID.randomUUID(); this.serviceId=serviceId; this.priority=priority; configure(response,resolution,warning,active);
    }
    public void configure(int response,int resolution,int warning,boolean active) {
        if(response<1 || resolution<response || resolution>31536000 || warning<1 || warning>99) throw new IllegalArgumentException("Invalid SLA targets");
        responseSeconds=response; resolutionSeconds=resolution; warningPercent=warning; this.active=active;
    }
    public UUID getId(){return id;}
    public UUID getServiceId(){return serviceId;}
    public IncidentPriority getPriority(){return priority;}
    public int getResponseSeconds(){return responseSeconds;}
    public int getResolutionSeconds(){return resolutionSeconds;}
    public int getWarningPercent(){return warningPercent;}
    public boolean isActive(){return active;}
}
