package edu.university.itsm.incident.automation;
import edu.university.itsm.incident.IncidentPriority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
public final class AutomationDtos {
    private AutomationDtos() {}
    public record Settings(@NotNull AssignmentMode strategy,boolean automaticAssignment) {}
    public record PolicyInput(UUID serviceId,@NotNull IncidentPriority priority,@Min(1) @Max(31536000) int responseSeconds,
                              @Min(1) @Max(31536000) int resolutionSeconds,@Min(1) @Max(99) int warningPercent,boolean active) {}
    public record PolicyView(UUID id,UUID serviceId,IncidentPriority priority,int responseSeconds,int resolutionSeconds,int warningPercent,boolean active) {
        public static PolicyView of(SlaPolicyEntity p){return new PolicyView(p.getId(),p.getServiceId(),p.getPriority(),p.getResponseSeconds(),p.getResolutionSeconds(),p.getWarningPercent(),p.isActive());}
    }
    public record Skill(@NotNull UUID categoryId,@Min(1) @Max(3) int proficiency) {}
    public record TechnicianInput(@Min(1) @Max(1000) int capacity,boolean available,@NotNull @Size(max=500) List<@NotNull @Valid Skill> skills) {}
    public record TechnicianView(UUID id,String displayName,int capacity,boolean available,long workload,List<Skill> skills) {}
    public record AuditView(Long id,String actor,String action,String description,Instant createdAt) {}
}
