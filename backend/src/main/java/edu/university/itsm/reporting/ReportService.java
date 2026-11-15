package edu.university.itsm.reporting;
import edu.university.itsm.identity.*;
import edu.university.itsm.incident.*;
import edu.university.itsm.shared.CurrentUser;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.time.*;
import java.util.*;
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ReportService {
    private final ReportRepository repository;
    private final Clock clock;
    public ReportService(ReportRepository repository,Clock clock){this.repository=repository;this.clock=clock;}
    public ReportDtos.Report report(User actor,IncidentDtos.Search filters){
        Instant now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);var scope=repository.scope(actor,filters,now);
        return new ReportDtos.Report(now,repository.summary(scope),repository.buckets(scope,"status"),repository.buckets(scope,"priority"),repository.buckets(scope,"category"),repository.buckets(scope,"sla"),repository.trends(scope),repository.serviceHealth(scope));
    }
    public ReportDtos.Page<ReportDtos.ServiceHealth> services(User actor,IncidentDtos.Search f,int page,int size){CurrentUser.requireRole(actor,Role.ADMIN);bounds(page,size);return repository.services(repository.scope(actor,f,clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)),page,size);}
    public ReportDtos.Page<ReportDtos.Technician> technicians(User actor,IncidentDtos.Search f,int page,int size){if(actor.getRole()==Role.EMPLOYEE)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Technician analytics require technician or administrator access");bounds(page,size);return repository.technicians(repository.scope(actor,f,clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)),actor,f.technicianId(),page,size);}
    public void export(User actor,IncidentDtos.Search f,String sort,String direction,HttpServletResponse response)throws IOException {
        IncidentSorting.sort(sort,direction);var scope=repository.scope(actor,f,clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        response.setContentType("text/csv;charset=UTF-8");response.setHeader(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=incidents.csv");response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
        var writer=response.getWriter();writer.write(Csv.row("Incident ID","Title","Status","Priority","Service","Category","Technician","Created UTC","Resolved UTC","Response SLA","Resolution SLA","SLA status"));
        // PostgreSQL cursor fetches 500 rows at a time inside this read-only transaction.
        repository.export(scope,sort,direction,r -> writer.write(Csv.row("INC-"+String.format(Locale.ROOT,"%06d",r.getLong("id")),r.getString("title"),r.getString("status"),r.getString("priority"),r.getString("service"),r.getString("category"),r.getString("technician"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("resolved_at")==null?null:r.getTimestamp("resolved_at").toInstant(),r.getString("response_outcome"),r.getString("resolution_outcome"),r.getString("sla_status"))));writer.flush();
    }
    private void bounds(int page,int size){if(page<0 || size<1 || size>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Page must be nonnegative and size between 1 and 100");}
}
