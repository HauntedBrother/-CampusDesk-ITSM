package edu.university.itsm.incident;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component @ConditionalOnProperty(name="app.sla.monitor-enabled",havingValue="true",matchIfMissing=true)
public class SlaMonitor {
    private static final Logger LOG=LoggerFactory.getLogger(SlaMonitor.class);
    private final SlaMonitorBatch batch;
    private long cursor;
    public SlaMonitor(SlaMonitorBatch batch){this.batch=batch;}
    @Scheduled(initialDelayString="${app.sla.monitor-initial-delay-ms:10000}",fixedDelayString="${app.sla.monitor-delay-ms:15000}")
    public void check() {
        try {do {cursor=batch.process(cursor);} while(cursor!=0);}
        catch(RuntimeException ex) {LOG.error("SLA monitor batch failed; it will retry on the next scheduled check",ex);}
    }
}
