package edu.university.itsm.incident;

import edu.university.itsm.identity.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static edu.university.itsm.incident.IncidentStatus.*;

@Component
public class IncidentPolicy {
    public boolean canRead(User actor,Incident incident) {
        return switch(actor.getRole()) {
            case ADMIN -> true;
            case EMPLOYEE -> actor.getId().equals(incident.getReporter().getId());
            case TECHNICIAN -> incident.getTechnician()!=null && actor.getId().equals(incident.getTechnician().getId());
        };
    }
    public void requireRead(User actor,Incident incident) {
        // The same response as a missing incident prevents disclosing another user's incident IDs.
        if(!canRead(actor,incident)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Incident not found");
    }
    public List<String> actions(User actor,Incident incident) {
        if(!canRead(actor,incident)) return List.of();
        boolean admin=actor.getRole()==Role.ADMIN, employee=actor.getRole()==Role.EMPLOYEE;
        IncidentStatus state=incident.getStatus();
        List<String> actions=new ArrayList<>();
        if((admin && state.ordinal()<RESOLVED.ordinal()) || (employee && (state==NEW || state==CLASSIFIED))) actions.add("UPDATE");
        if(state!=CLOSED) actions.add("COMMENT");
        if(admin && state==NEW) actions.add("CLASSIFY");
        if(admin && (state==CLASSIFIED || state==ASSIGNED)) actions.add("ASSIGN");
        if(admin && (state==NEW || state==CLASSIFIED)) actions.add("AUTO_ASSIGN");
        if(actor.getRole()==Role.TECHNICIAN) {
            if(state==ASSIGNED) actions.add("ACKNOWLEDGE");
            if(state==ACKNOWLEDGED) actions.add("START");
            if(state==IN_PROGRESS) actions.add("RESOLVE");
        }
        if(employee && state==RESOLVED) actions.add("VERIFY");
        if((employee || admin) && state==VERIFIED) actions.add("CLOSE");
        if(employee && state.ordinal()>=RESOLVED.ordinal()) actions.add("FEEDBACK");
        if(admin) actions.add("OVERRIDE");
        return List.copyOf(actions);
    }
    public void requireAction(User actor,Incident incident,String action) {
        requireRead(actor,incident);
        boolean roleAllowed=switch(action) {
            case "CLASSIFY","ASSIGN","OVERRIDE" -> actor.getRole()==Role.ADMIN;
            case "ACKNOWLEDGE","START","RESOLVE" -> actor.getRole()==Role.TECHNICIAN;
            case "VERIFY","FEEDBACK" -> actor.getRole()==Role.EMPLOYEE;
            case "UPDATE","CLOSE" -> actor.getRole()!=Role.TECHNICIAN;
            case "COMMENT" -> true;
            default -> false;
        };
        if(!roleAllowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This operation is not permitted for your role");
        if(!actions(actor,incident).contains(action)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Action is not allowed for your role in the current incident state");
    }
}
