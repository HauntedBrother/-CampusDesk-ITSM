package edu.university.itsm.incident;
import java.util.*;
public final class AuditSnapshot {
    private AuditSnapshot() {}
    private static String text(Object value){return value==null ? null : value.toString();}
    public static Map<String,Object> of(Incident i) {
        Map<String,Object> v=new LinkedHashMap<>();
        v.put("title",i.getTitle());v.put("description",i.getDescription());v.put("reporterId",text(i.getReporter().getId()));
        v.put("serviceId",text(i.getService().getId()));v.put("categoryId",text(i.getCategory().getId()));
        v.put("impact",i.getImpact());v.put("urgency",i.getUrgency());v.put("priority",text(i.getPriority()));v.put("priorityScore",i.getPriorityScore());v.put("criticality",i.getPriorityCriticality());
        v.put("status",text(i.getStatus()));v.put("technicianId",i.getTechnician()==null ? null : text(i.getTechnician().getId()));v.put("assignmentIssue",i.getAssignmentIssue());
        v.put("resolution",i.getResolution());v.put("feedback",i.getFeedback());v.put("feedbackRating",i.getFeedbackRating());
        v.put("acknowledgedAt",text(i.getAcknowledgedAt()));v.put("resolvedAt",text(i.getResolvedAt()));v.put("verifiedAt",text(i.getVerifiedAt()));v.put("closedAt",text(i.getClosedAt()));
        SlaSnapshot sla=i.getSla();v.put("slaPolicyId",text(sla.getPolicyId()));v.put("responseDueAt",text(sla.getResponseDueAt()));v.put("resolutionDueAt",text(sla.getResolutionDueAt()));
        v.put("responseSeconds",sla.getResponseSeconds());v.put("resolutionSeconds",sla.getResolutionSeconds());v.put("warningPercent",sla.getWarningPercent());v.put("firstAcknowledgedAt",text(sla.getFirstAcknowledgedAt()));v.put("firstResponseAt",text(sla.getFirstResponseAt()));
        v.put("responseBreachedAt",text(sla.getResponseBreachedAt()));v.put("resolutionBreachedAt",text(sla.getResolutionBreachedAt()));v.put("slaIssue",sla.getIssue());
        v.put("responseWarnedAt",text(sla.getResponseWarnedAt()));v.put("resolutionWarnedAt",text(sla.getResolutionWarnedAt()));
        return v;
    }
}
