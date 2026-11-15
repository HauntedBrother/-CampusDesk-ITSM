package edu.university.itsm.incident;
import edu.university.itsm.identity.User;
import edu.university.itsm.notification.NotificationEvents;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class IncidentAudit {
    private final IncidentHistoryRepository history;
    private final NotificationEvents notifications;
    public IncidentAudit(IncidentHistoryRepository history,NotificationEvents notifications){this.history=history;this.notifications=notifications;}
    public void record(Incident incident,User actor,String action,IncidentStatus previous,String description,boolean internal,Map<String,Object> before) {
        record(incident,actor,action,previous,description,internal,before,AuditSnapshot.of(incident));
    }
    public void record(Incident incident,User actor,String action,IncidentStatus previous,String description,boolean internal,Map<String,Object> before,Map<String,Object> after) {
        IncidentHistory entry=new IncidentHistory(incident,actor,action,previous,description,internal);
        entry.values(before,after);history.saveAndFlush(entry);notifications.queue(entry,incident);
    }
}
