package edu.university.itsm.incident;

import edu.university.itsm.identity.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.ZoneOffset;
import java.util.Locale;

public final class IncidentSpecifications {
    private IncidentSpecifications() {}
    public static Specification<Incident> visibleTo(User actor,IncidentDtos.Search search) {
        if((search.createdFrom()!=null && (search.createdFrom().getYear()<1 || search.createdFrom().getYear()>9999)) || (search.createdTo()!=null && (search.createdTo().getYear()<1 || search.createdTo().getYear()>9999)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Dates must use years 0001 to 9999");
        if(search.createdFrom()!=null && search.createdTo()!=null && search.createdFrom().isAfter(search.createdTo()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Start date must not follow end date");
        Specification<Incident> scope=(root,query,cb) -> switch(actor.getRole()) {
            case ADMIN -> cb.conjunction();
            case EMPLOYEE -> cb.equal(root.get("reporter").get("id"),actor.getId());
            case TECHNICIAN -> cb.equal(root.get("technician").get("id"),actor.getId());
        };
        if(search.id()!=null) {
            long id;
            try { id=Long.parseLong(search.id().toUpperCase(Locale.ROOT).replace("INC-","")); }
            catch(NumberFormatException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid incident ID"); }
            scope=scope.and((r,q,c) -> c.equal(r.get("id"),id));
        }
        if(search.status()!=null) scope=scope.and((r,q,c) -> c.equal(r.get("status"),search.status()));
        if(search.categoryId()!=null) scope=scope.and((r,q,c) -> c.equal(r.get("category").get("id"),search.categoryId()));
        if(search.serviceId()!=null) scope=scope.and((r,q,c) -> c.equal(r.get("service").get("id"),search.serviceId()));
        if(search.technicianId()!=null) scope=scope.and((r,q,c) -> c.equal(r.get("technician").get("id"),search.technicianId()));
        if(search.priority()!=null) scope=scope.and((r,q,c) -> search.priority().equals("UNSET") ? c.isNull(r.get("priority")) : c.equal(r.get("priority"),IncidentPriority.valueOf(search.priority())));
        if(search.createdFrom()!=null) scope=scope.and((r,q,c) -> c.greaterThanOrEqualTo(r.get("createdAt"),search.createdFrom().atStartOfDay().toInstant(ZoneOffset.UTC)));
        if(search.createdTo()!=null) scope=scope.and((r,q,c) -> c.lessThan(r.get("createdAt"),search.createdTo().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
        if(search.slaStatus()!=null) scope=scope.and((r,q,c) -> {
            var sla=r.get("sla");
            return c.equal(c.function("itsm_incident_sla",String.class,sla.get("responseDueAt"),sla.get("resolutionDueAt"),sla.get("firstResponseAt"),r.get("resolvedAt"),sla.get("responseBreachedAt"),sla.get("resolutionBreachedAt"),sla.get("slaResponseSeconds"),sla.get("slaResolutionSeconds"),sla.get("slaWarningPercent"),c.literal(java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS))),search.slaStatus().name());
        });
        return scope;
    }
}
