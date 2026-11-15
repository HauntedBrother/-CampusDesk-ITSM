package edu.university.itsm.incident;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class SlaMonitorBatch {
    private final IncidentAutomation automation;
    private final IncidentRepository incidents;
    public SlaMonitorBatch(IncidentAutomation automation,IncidentRepository incidents){this.automation=automation; this.incidents=incidents;}
    @Transactional public long process(long after) {
        automation.lock();
        // Include resolved/closed legacy rows until their outcome has been evaluated as well.
        List<Long> ids=incidents.scanIds(IncidentAutomation.OPEN,after,PageRequest.of(0,100));
        for(Long id:ids) incidents.lockById(id).ifPresent(incident -> automation.refresh(incident,null,false));
        return ids.isEmpty() ? 0 : ids.getLast();
    }
}
