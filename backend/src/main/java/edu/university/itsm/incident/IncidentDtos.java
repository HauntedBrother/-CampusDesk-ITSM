package edu.university.itsm.incident;

import edu.university.itsm.catalog.CatalogDtos;
import edu.university.itsm.identity.User;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.*;
import java.util.*;

public final class IncidentDtos {
    private IncidentDtos() {}
    public record Input(@NotBlank @Size(max=200) String title,@NotBlank @Size(max=10000) String description,
                        @NotNull UUID serviceId,UUID categoryId,@Min(1) @Max(3) int impact,@Min(1) @Max(3) int urgency) {}
    public record Assignment(@NotNull UUID technicianId,@Size(max=2000) String reason) {
        public Assignment(UUID technicianId){this(technicianId,null);}
    }
    public record Resolution(@NotBlank @Size(max=10000) String resolution) {}
    public record Comment(@NotBlank @Size(max=5000) String body,boolean internal) {}
    public record Feedback(@Min(1) @Max(5) int rating,@NotBlank @Size(max=2000) String feedback) {}
    public record Override(@NotNull IncidentStatus target,@NotBlank @Size(min=10,max=2000) String reason) {}
    public record Search(@Pattern(regexp="(?i)^(INC-)?[0-9]{1,18}$") String id,IncidentStatus status,UUID categoryId,
                         UUID serviceId,@Pattern(regexp="^(P[1-4]|UNSET)$") String priority,UUID technicianId,
                         @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate createdFrom,
                         @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate createdTo,SlaSnapshot.Outcome slaStatus) {
        public Search(String id,IncidentStatus status,UUID categoryId,UUID serviceId,String priority,UUID technicianId,LocalDate createdFrom,LocalDate createdTo){this(id,status,categoryId,serviceId,priority,technicianId,createdFrom,createdTo,null);}
    }
    public record Person(UUID id,String displayName) {
        static Person of(User user) { return user==null ? null : new Person(user.getId(),user.getDisplayName()); }
    }
    public record View(Long id,String reference,long version,String title,String description,Person reporter,
                       CatalogDtos.ServiceView service,CatalogDtos.CategoryView category,int impact,int urgency,
                       IncidentPriority priority,IncidentStatus status,Person technician,String resolution,
                       String feedback,Integer feedbackRating,Instant createdAt,Instant updatedAt,Instant acknowledgedAt,
                       Instant resolvedAt,Instant verifiedAt,Instant closedAt,Integer priorityScore,Integer priorityCriticality,
                       String assignmentIssue,SlaView sla) {
        static View of(Incident i) {
            return new View(i.getId(),i.getReference(),i.getVersion(),i.getTitle(),i.getDescription(),Person.of(i.getReporter()),
                CatalogDtos.ServiceView.of(i.getService()),CatalogDtos.CategoryView.of(i.getCategory()),i.getImpact(),i.getUrgency(),
                i.getPriority(),i.getStatus(),Person.of(i.getTechnician()),i.getResolution(),i.getFeedback(),i.getFeedbackRating(),
                i.getCreatedAt(),i.getUpdatedAt(),i.getAcknowledgedAt(),i.getResolvedAt(),i.getVerifiedAt(),i.getClosedAt(),
                i.getPriorityScore(),i.getPriorityCriticality(),i.getAssignmentIssue(),SlaView.of(i));
        }
    }
    public record CommentView(Long id,Person author,String body,boolean internal,Instant createdAt) {
        static CommentView of(IncidentComment c) { return new CommentView(c.getId(),Person.of(c.getAuthor()),c.getBody(),c.isInternal(),c.getCreatedAt()); }
    }
    public record HistoryView(Long id,Person actor,String action,IncidentStatus previousStatus,IncidentStatus newStatus,String description,boolean internal,Instant createdAt,Long incidentId,Map<String,Object> oldValues,Map<String,Object> newValues) {
        static HistoryView of(IncidentHistory h) { return new HistoryView(h.getId(),h.getActor()==null ? new Person(null,"System") : Person.of(h.getActor()),h.getAction(),h.getPreviousStatus(),h.getNewStatus(),h.getDescription(),h.isInternal(),h.getCreatedAt(),h.getIncidentId(),h.getOldValues(),h.getNewValues()); }
    }
    public record Detail(View incident,List<CommentView> comments,List<HistoryView> history,List<String> actions) {}
    public record Page(List<View> content,long totalElements,int totalPages,int page,int size) {}
    public record SlaView(UUID policyId,Integer responseSeconds,Integer resolutionSeconds,Integer warningPercent,
                          Instant responseDueAt,Instant resolutionDueAt,Instant firstAcknowledgedAt,Instant firstResponseAt,
                          Instant responseBreachedAt,Instant resolutionBreachedAt,SlaSnapshot.Outcome responseStatus,
                          SlaSnapshot.Outcome resolutionStatus,Instant evaluatedAt,String issue,Instant serverTime) {
        static SlaView of(Incident incident) {
            SlaSnapshot s=incident.getSla(); Instant now=Instant.now();
            return new SlaView(s.getPolicyId(),s.getResponseSeconds(),s.getResolutionSeconds(),s.getWarningPercent(),s.getResponseDueAt(),s.getResolutionDueAt(),
                s.getFirstAcknowledgedAt(),s.getFirstResponseAt(),s.getResponseBreachedAt(),s.getResolutionBreachedAt(),s.responseOutcome(now),
                s.resolutionOutcome(now,incident.getResolvedAt()),s.getEvaluatedAt(),s.getIssue(),now);
        }
    }
}
