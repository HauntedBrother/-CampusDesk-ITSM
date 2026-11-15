package edu.university.itsm.incident;
import edu.university.itsm.incident.automation.*;
import org.junit.jupiter.api.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
class SlaSnapshotTest {
    Instant start=Instant.parse("2026-11-15T00:00:00Z");
    SlaSnapshot snapshot(){SlaSnapshot s=new SlaSnapshot();s.configure(new SlaPolicyEntity(null,IncidentPriority.P1,300,1800,80,true),start);return s;}
    @Test void deadlinesUseCalendarSecondsAcrossMidnightAndWeekend(){SlaSnapshot s=new SlaSnapshot();s.configure(new SlaPolicyEntity(null,IncidentPriority.P4,14400,86400,80,true),start);assertEquals(start.plusSeconds(86400),s.getResolutionDueAt());assertEquals(start.plusSeconds(14400),s.getResponseDueAt());}
    @Test void approachingThresholdIsExactAndWarningIsRecordedOnce(){var s=snapshot();assertEquals(SlaSnapshot.Outcome.TRACKING,s.responseOutcome(start.plusSeconds(239)));assertEquals(SlaSnapshot.Outcome.APPROACHING,s.responseOutcome(start.plusSeconds(240)));assertTrue(s.evaluate(start.plusSeconds(240),null).contains("SLA_RESPONSE_APPROACHING"));assertTrue(s.evaluate(start.plusSeconds(241),null).isEmpty());}
    @Test void exactlyAtDeadlineIsNotLateButOneNanosecondAfterIs(){var s=snapshot();assertNotEquals(SlaSnapshot.Outcome.BREACHED,s.responseOutcome(start.plusSeconds(300)));assertEquals(SlaSnapshot.Outcome.BREACHED,s.responseOutcome(start.plusSeconds(300).plusNanos(1)));}
    @Test void onTimeAcknowledgementStopsResponseClock(){var s=snapshot();s.syncMilestones(start.plusSeconds(300),null);s.evaluate(start.plusSeconds(900),null);assertEquals(SlaSnapshot.Outcome.MET,s.responseOutcome(start.plusSeconds(900)));assertNull(s.getResponseBreachedAt());}
    @Test void lateAcknowledgementIsStillABreach(){var s=snapshot();s.syncMilestones(start.plusSeconds(301),null);assertTrue(s.evaluate(start.plusSeconds(301),null).contains("SLA_RESPONSE_BREACHED"));assertEquals(start.plusSeconds(300),s.getResponseBreachedAt());}
    @Test void resolutionDeadlineAndBreachAreIndependent(){var s=snapshot();s.syncMilestones(start.plusSeconds(100),start.plusSeconds(1801));assertTrue(s.evaluate(start.plusSeconds(1801),start.plusSeconds(1801)).contains("SLA_RESOLUTION_BREACHED"));assertNull(s.getResponseBreachedAt());assertEquals(start.plusSeconds(1800),s.getResolutionBreachedAt());}
    @Test void noDuplicateViolationRecordsAcrossChecks(){var s=snapshot();assertEquals(2,s.evaluate(start.plusSeconds(1900),null).size());assertTrue(s.evaluate(start.plusSeconds(2000),null).isEmpty());}
    @Test void missingPolicyIsExplicitAndDoesNotFabricateDeadlines(){var s=new SlaSnapshot();s.missingPolicy();assertNotNull(s.getIssue());assertNull(s.getResponseDueAt());assertEquals(SlaSnapshot.Outcome.UNCONFIGURED,s.responseOutcome(start));}
    @Test void removalOfPolicyRetainsExistingContract(){var s=snapshot();s.missingPolicy();assertEquals(start.plusSeconds(300),s.getResponseDueAt());assertTrue(s.getIssue().contains("retained"));}
    @Test void recalculationRetainsClockAndRecordedBreaches(){var s=snapshot();s.evaluate(start.plusSeconds(301),null);s.configure(new SlaPolicyEntity(null,IncidentPriority.P4,14400,86400,80,true),start);assertEquals(start.plusSeconds(14400),s.getResponseDueAt());assertEquals(start.plusSeconds(300),s.getResponseBreachedAt());assertEquals(SlaSnapshot.Outcome.BREACHED,s.responseOutcome(start.plusSeconds(302)));}
    @Test void firstAcknowledgementSurvivesLifecycleReopening(){var s=snapshot();s.syncMilestones(start.plusSeconds(100),null);s.syncMilestones(null,null);s.syncMilestones(start.plusSeconds(500),null);assertEquals(start.plusSeconds(100),s.getFirstAcknowledgedAt());assertEquals(start.plusSeconds(100),s.getFirstResponseAt());}
    @Test void resolutionWithoutAcknowledgementDoesNotInventAnAcknowledgement(){var s=snapshot();s.syncMilestones(null,start.plusSeconds(200));assertNull(s.getFirstAcknowledgedAt());assertEquals(start.plusSeconds(200),s.getFirstResponseAt());}
    @Test void invalidPoliciesRejected(){assertThrows(IllegalArgumentException.class,() -> new SlaPolicyEntity(null,IncidentPriority.P1,300,200,80,true));assertThrows(IllegalArgumentException.class,() -> new SlaPolicyEntity(null,IncidentPriority.P1,300,1800,100,true));}
    @Test void firstResponseIsTheEarliestActualAcknowledgementOrResolution(){var s=snapshot();s.syncMilestones(start.plusSeconds(400),start.plusSeconds(200));assertEquals(start.plusSeconds(200),s.getFirstResponseAt());assertEquals(start.plusSeconds(400),s.getFirstAcknowledgedAt());}
}
