package edu.university.itsm.incident;

import edu.university.itsm.incident.automation.SlaPolicyEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Embeddable
public class SlaSnapshot {
    private UUID slaPolicyId;
    private Integer slaResponseSeconds;
    private Integer slaResolutionSeconds;
    private Integer slaWarningPercent;
    private Instant responseDueAt;
    private Instant resolutionDueAt;
    private Instant firstAcknowledgedAt;
    private Instant firstResponseAt;
    private Instant responseBreachedAt;
    private Instant resolutionBreachedAt;
    private Instant responseWarnedAt;
    private Instant resolutionWarnedAt;
    @Column(length=500) private String slaIssue;
    private Instant slaEvaluatedAt;
    public SlaSnapshot() {}
    public boolean configure(SlaPolicyEntity policy,Instant createdAt) {
        boolean changed=!Objects.equals(slaPolicyId,policy.getId()) || !Objects.equals(slaResponseSeconds,policy.getResponseSeconds())
            || !Objects.equals(slaResolutionSeconds,policy.getResolutionSeconds()) || !Objects.equals(slaWarningPercent,policy.getWarningPercent());
        slaIssue=null;
        if(changed) {
            slaPolicyId=policy.getId(); slaResponseSeconds=policy.getResponseSeconds(); slaResolutionSeconds=policy.getResolutionSeconds(); slaWarningPercent=policy.getWarningPercent();
            responseDueAt=createdAt.plusSeconds(slaResponseSeconds); resolutionDueAt=createdAt.plusSeconds(slaResolutionSeconds);
            responseWarnedAt=null; resolutionWarnedAt=null;
        }
        return changed;
    }
    public void missingPolicy(){slaIssue=slaPolicyId==null ? "No active SLA policy. Configure a service policy or global default." : "No active policy found; previously agreed SLA targets are retained.";}
    public void syncMilestones(Instant acknowledgement,Instant resolution) {
        if(acknowledgement!=null && (firstAcknowledgedAt==null || acknowledgement.isBefore(firstAcknowledgedAt))) firstAcknowledgedAt=acknowledgement;
        Instant response=firstAcknowledgedAt!=null ? firstAcknowledgedAt : resolution;
        if(resolution!=null && (response==null || resolution.isBefore(response))) response=resolution;
        if(response!=null && (firstResponseAt==null || response.isBefore(firstResponseAt))) firstResponseAt=response;
    }
    public enum Outcome { UNCONFIGURED, TRACKING, APPROACHING, MET, BREACHED }
    public Outcome responseOutcome(Instant now){return outcome(responseDueAt,firstResponseAt,responseBreachedAt,slaResponseSeconds,now);}
    public Outcome resolutionOutcome(Instant now,Instant resolution){return outcome(resolutionDueAt,resolution,resolutionBreachedAt,slaResolutionSeconds,now);}
    private Outcome outcome(Instant due,Instant completed,Instant breach,Integer target,Instant now) {
        if(breach!=null) return Outcome.BREACHED;
        if(due==null) return Outcome.UNCONFIGURED;
        if((completed!=null ? completed : now).isAfter(due)) return Outcome.BREACHED;
        if(completed!=null) return Outcome.MET;
        Instant warning=due.minusMillis((long)target*1000*(100-slaWarningPercent)/100);
        return !now.isBefore(warning) ? Outcome.APPROACHING : Outcome.TRACKING;
    }
    /** Returns newly observed events only; breach evidence survives reprioritization/reopening. */
    public List<String> evaluate(Instant now,Instant resolution) {
        List<String> events=new ArrayList<>();
        Outcome response=responseOutcome(now), resolve=resolutionOutcome(now,resolution);
        if(response==Outcome.BREACHED && responseBreachedAt==null) {responseBreachedAt=responseDueAt; events.add("SLA_RESPONSE_BREACHED");}
        if(resolve==Outcome.BREACHED && resolutionBreachedAt==null) {resolutionBreachedAt=resolutionDueAt; events.add("SLA_RESOLUTION_BREACHED");}
        if(response==Outcome.APPROACHING && responseWarnedAt==null) {responseWarnedAt=now; events.add("SLA_RESPONSE_APPROACHING");}
        if(resolve==Outcome.APPROACHING && resolutionWarnedAt==null) {resolutionWarnedAt=now; events.add("SLA_RESOLUTION_APPROACHING");}
        slaEvaluatedAt=now; return events;
    }
    public UUID getPolicyId(){return slaPolicyId;}
    public Integer getResponseSeconds(){return slaResponseSeconds;}
    public Integer getResolutionSeconds(){return slaResolutionSeconds;}
    public Integer getWarningPercent(){return slaWarningPercent;}
    public Instant getResponseDueAt(){return responseDueAt;}
    public Instant getResolutionDueAt(){return resolutionDueAt;}
    public Instant getFirstAcknowledgedAt(){return firstAcknowledgedAt;}
    public Instant getFirstResponseAt(){return firstResponseAt;}
    public Instant getResponseBreachedAt(){return responseBreachedAt;}
    public Instant getResolutionBreachedAt(){return resolutionBreachedAt;}
    public Instant getResponseWarnedAt(){return responseWarnedAt;}
    public Instant getResolutionWarnedAt(){return resolutionWarnedAt;}
    public Instant getEvaluatedAt(){return slaEvaluatedAt;}
    public String getIssue(){return slaIssue;}
    public String describe(){return "policy="+slaPolicyId+", response="+responseDueAt+", resolution="+resolutionDueAt+", warning="+slaWarningPercent+"%";}
}
