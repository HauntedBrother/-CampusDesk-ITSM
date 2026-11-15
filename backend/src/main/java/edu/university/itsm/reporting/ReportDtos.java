package edu.university.itsm.reporting;
import java.time.Instant;
import java.util.*;
public final class ReportDtos {
    private ReportDtos() {}
    public record Summary(long total,long open,long resolved,long critical,long slaViolations,long responseViolations,long resolutionViolations,long completedWithTargets,long onTimeCompleted,Double compliancePercent,Double averageResolutionSeconds,Double averageResponseSeconds,Double averageFeedback) {}
    public record Bucket(String key,String label,long count) {}
    public record Trend(String month,long created,long resolved) {}
    public record ServiceHealth(UUID id,String name,long total,long open,long critical,long breached,String health) {}
    public record Technician(UUID id,String displayName,boolean enabled,boolean available,int capacity,long active,long assignments,long resolved,Double averageResolutionSeconds,long breached) {}
    public record Page<T>(List<T> content,long totalElements,int page,int totalPages) {}
    public record Report(Instant observedAt,Summary summary,List<Bucket> statuses,List<Bucket> priorities,List<Bucket> categories,List<Bucket> sla,List<Trend> trends,List<Bucket> serviceHealth) {}
}
