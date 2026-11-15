package edu.university.itsm.incident.automation;
import edu.university.itsm.incident.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CalendarSlaPolicyTest {
    SlaPolicyRepository repository=mock(SlaPolicyRepository.class);
    CalendarSlaPolicy policy=new CalendarSlaPolicy(repository);
    UUID service=UUID.randomUUID();
    @Test void servicePolicyTakesPrecedence(){var specific=new SlaPolicyEntity(service,IncidentPriority.P1,60,600,80,true);when(repository.findByServiceIdAndPriorityAndActiveTrue(service,IncidentPriority.P1)).thenReturn(Optional.of(specific));assertSame(specific,policy.applicable(service,IncidentPriority.P1).orElseThrow());verify(repository,never()).findByServiceIdIsNullAndPriorityAndActiveTrue(any());}
    @Test void fallsBackToGlobalDefault(){var global=new SlaPolicyEntity(null,IncidentPriority.P2,900,7200,80,true);when(repository.findByServiceIdIsNullAndPriorityAndActiveTrue(IncidentPriority.P2)).thenReturn(Optional.of(global));Instant start=Instant.parse("2026-11-15T23:50:00Z");assertEquals(start.plusSeconds(7200),policy.calculate(service,IncidentPriority.P2,start).resolutionDueAt());}
    @Test void missingConfigurationHasNoFakeDefault(){assertTrue(policy.applicable(service,IncidentPriority.P1).isEmpty());assertThrows(InvalidIncidentOperation.class,() -> policy.calculate(service,IncidentPriority.P1,Instant.now()));}
}
