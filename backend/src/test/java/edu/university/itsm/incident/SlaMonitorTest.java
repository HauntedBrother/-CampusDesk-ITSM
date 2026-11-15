package edu.university.itsm.incident;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
class SlaMonitorTest {
    @Test void drainsAllBatchesWithoutHoldingOneTransactionForEntireScan(){SlaMonitorBatch batch=mock(SlaMonitorBatch.class);when(batch.process(0)).thenReturn(100L);when(batch.process(100)).thenReturn(200L);when(batch.process(200)).thenReturn(0L);new SlaMonitor(batch).check();verify(batch).process(0);verify(batch).process(100);verify(batch).process(200);}
    @Test void failureRetainsCursorForTheNextScheduledRetry(){SlaMonitorBatch batch=mock(SlaMonitorBatch.class);when(batch.process(0)).thenReturn(100L,0L);when(batch.process(100)).thenThrow(new InvalidIncidentOperation("Transient failure")).thenReturn(0L);SlaMonitor monitor=new SlaMonitor(batch);monitor.check();monitor.check();verify(batch,times(2)).process(100);}
}
