package edu.university.itsm.reporting;

import edu.university.itsm.identity.*;
import edu.university.itsm.incident.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Read projections only: PostgreSQL aggregates and pages; no incident entity hydration. */
@Repository
public class ReportRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public ReportRepository(DataSource source){var template=new JdbcTemplate(source);template.setFetchSize(500);jdbc=new NamedParameterJdbcTemplate(template);}
    public record Scope(String cte,MapSqlParameterSource parameters) {}
    public Scope scope(User actor,IncidentDtos.Search f,Instant now){
        // Reuse the domain validation for inclusive UTC creation-date ranges and IDs.
        IncidentSpecifications.visibleTo(actor,f);
        var p=new MapSqlParameterSource("now",Timestamp.from(now));
        StringBuilder where=new StringBuilder(" where 1=1");
        if(actor.getRole()==Role.EMPLOYEE){where.append(" and i.reporter_id=:actor");p.addValue("actor",actor.getId());}
        if(actor.getRole()==Role.TECHNICIAN){where.append(" and i.technician_id=:actor");p.addValue("actor",actor.getId());}
        if(f.id()!=null){where.append(" and i.id=:id");p.addValue("id",Long.parseLong(f.id().toUpperCase(Locale.ROOT).replace("INC-","")));}
        if(f.status()!=null){where.append(" and i.status=:status");p.addValue("status",f.status().name());}
        if(f.priority()!=null){if(f.priority().equals("UNSET"))where.append(" and i.priority is null");else{where.append(" and i.priority=:priority");p.addValue("priority",f.priority());}}
        if(f.categoryId()!=null){where.append(" and i.category_id=:category");p.addValue("category",f.categoryId());}
        if(f.serviceId()!=null){where.append(" and i.service_id=:service");p.addValue("service",f.serviceId());}
        if(f.technicianId()!=null){where.append(" and i.technician_id=:technician");p.addValue("technician",f.technicianId());}
        if(f.createdFrom()!=null){where.append(" and i.created_at>=:from");p.addValue("from",Timestamp.from(f.createdFrom().atStartOfDay().toInstant(ZoneOffset.UTC)));}
        if(f.createdTo()!=null){where.append(" and i.created_at<:to");p.addValue("to",Timestamp.from(f.createdTo().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));}
        String cte="with measured as (select i.*,itsm_sla_outcome(response_due_at,first_response_at,response_breached_at,sla_response_seconds,sla_warning_percent,:now) response_outcome,itsm_sla_outcome(resolution_due_at,resolved_at,resolution_breached_at,sla_resolution_seconds,sla_warning_percent,:now) resolution_outcome,itsm_incident_sla(response_due_at,resolution_due_at,first_response_at,resolved_at,response_breached_at,resolution_breached_at,sla_response_seconds,sla_resolution_seconds,sla_warning_percent,:now) sla_status from incidents i"+where+"), selected as (select * from measured"+(f.slaStatus()==null ? "" : " where sla_status=:sla")+") ";
        if(f.slaStatus()!=null)p.addValue("sla",f.slaStatus().name());
        return new Scope(cte,p);
    }
    private static final String OPEN="status in ('NEW','CLASSIFIED','ASSIGNED','ACKNOWLEDGED','IN_PROGRESS')";
    private static final String COMPLETE="status in ('RESOLVED','VERIFIED','CLOSED')";
    private static final String ELIGIBLE=COMPLETE+" and response_due_at is not null and resolution_due_at is not null";
    public ReportDtos.Summary summary(Scope s){return jdbc.queryForObject(s.cte()+"select count(*) total,count(*) filter(where "+OPEN+") opened,count(*) filter(where "+COMPLETE+") resolved,count(*) filter(where "+OPEN+" and priority='P1') critical,count(*) filter(where sla_status='BREACHED') violations,count(*) filter(where response_outcome='BREACHED') response_violations,count(*) filter(where resolution_outcome='BREACHED') resolution_violations,count(*) filter(where "+ELIGIBLE+") eligible,count(*) filter(where "+ELIGIBLE+" and response_outcome='MET' and resolution_outcome='MET') compliant,avg(extract(epoch from resolved_at-created_at)) filter(where "+COMPLETE+" and resolved_at>=created_at) resolution_average,avg(extract(epoch from first_response_at-created_at)) filter(where first_response_at>=created_at) response_average,avg(feedback_rating) feedback_average from selected",s.parameters(),(r,n) -> {
        long eligible=r.getLong("eligible"),compliant=r.getLong("compliant");return new ReportDtos.Summary(r.getLong("total"),r.getLong("opened"),r.getLong("resolved"),r.getLong("critical"),r.getLong("violations"),r.getLong("response_violations"),r.getLong("resolution_violations"),eligible,compliant,eligible==0?null:100.0*compliant/eligible,nullable(r,"resolution_average"),nullable(r,"response_average"),nullable(r,"feedback_average"));});}
    public List<ReportDtos.Bucket> buckets(Scope s,String dimension){
        String expression=switch(dimension){case "status" -> "i.status";case "priority" -> "coalesce(i.priority,'UNSET')";case "sla" -> "i.sla_status";case "category" -> "i.category_id::text";default -> throw new IllegalArgumentException("Unknown report dimension");};
        String label=dimension.equals("category")?"c.name":expression;
        return jdbc.query(s.cte()+"select "+expression+" as key,"+label+" label,count(*) amount from selected i"+(dimension.equals("category")?" join incident_categories c on c.id=i.category_id":"")+" group by 1,2 order by amount desc,key",s.parameters(),(r,n) -> new ReportDtos.Bucket(r.getString("key"),r.getString("label"),r.getLong("amount")));
    }
    public List<ReportDtos.Trend> trends(Scope s){return jdbc.query(s.cte()+"select to_char(created_at at time zone 'UTC','YYYY-MM') month_key,count(*) created,count(*) filter(where "+COMPLETE+") resolved from selected group by 1 order by 1",s.parameters(),(r,n) -> new ReportDtos.Trend(r.getString("month_key"),r.getLong("created"),r.getLong("resolved")));}
    private String serviceGroups(Scope s){return s.cte()+", service_groups as (select service_id,count(*) total,count(*) filter(where "+OPEN+") opened,count(*) filter(where "+OPEN+" and priority='P1') critical,count(*) filter(where "+OPEN+" and sla_status='BREACHED') breached from selected group by service_id), health as (select *,case when critical>0 or breached>0 then 'CRITICAL' when opened>0 then 'DEGRADED' else 'CLEAR' end health from service_groups) ";}
    public List<ReportDtos.Bucket> serviceHealth(Scope s){return jdbc.query(serviceGroups(s)+"select health,count(*) amount from health group by health order by health",s.parameters(),(r,n) -> new ReportDtos.Bucket(r.getString("health"),r.getString("health"),r.getLong("amount")));}
    public ReportDtos.Page<ReportDtos.ServiceHealth> services(Scope s,int page,int size){
        long total=jdbc.queryForObject(s.cte()+"select count(distinct service_id) from selected",s.parameters(),Long.class);s.parameters().addValue("limit",size).addValue("offset",(long)page*size);
        var rows=jdbc.query(serviceGroups(s)+"select h.*,s.name from health h join it_services s on s.id=h.service_id order by critical desc,breached desc,opened desc,s.name,s.id limit :limit offset :offset",s.parameters(),(r,n) -> new ReportDtos.ServiceHealth(r.getObject("service_id",UUID.class),r.getString("name"),r.getLong("total"),r.getLong("opened"),r.getLong("critical"),r.getLong("breached"),r.getString("health")));
        return new ReportDtos.Page<>(rows,total,page,(int)Math.ceil((double)total/size));
    }
    public ReportDtos.Page<ReportDtos.Technician> technicians(Scope s,User actor,UUID selected,int page,int size){
        String filter=actor.getRole()==Role.TECHNICIAN?" and u.id=:user":selected==null?"":" and u.id=:user";
        if(actor.getRole()==Role.TECHNICIAN || selected!=null)s.parameters().addValue("user",actor.getRole()==Role.TECHNICIAN?actor.getId():selected);
        long total=jdbc.queryForObject("select count(*) from users u join technician_profiles p on p.user_id=u.id where u.role_name='TECHNICIAN'"+filter,s.parameters(),Long.class);
        s.parameters().addValue("limit",size).addValue("offset",(long)page*size);
        String sql=s.cte()+", cohort as (select technician_id,count(*) assignments,count(*) filter(where "+COMPLETE+") resolved,avg(extract(epoch from resolved_at-created_at)) filter(where "+COMPLETE+" and resolved_at>=created_at) average,count(*) filter(where sla_status='BREACHED') breached from selected group by technician_id), live as (select technician_id,count(*) active from incidents where "+OPEN+" group by technician_id) select u.id,u.display_name,u.enabled,p.available,p.capacity,coalesce(l.active,0) active,coalesce(c.assignments,0) assignments,coalesce(c.resolved,0) resolved,c.average,coalesce(c.breached,0) breached from users u join technician_profiles p on p.user_id=u.id left join cohort c on c.technician_id=u.id left join live l on l.technician_id=u.id where u.role_name='TECHNICIAN'"+filter+" order by active desc,u.display_name,u.id limit :limit offset :offset";
        var rows=jdbc.query(sql,s.parameters(),(r,n) -> new ReportDtos.Technician(r.getObject("id",UUID.class),r.getString("display_name"),r.getBoolean("enabled"),r.getBoolean("available"),r.getInt("capacity"),r.getLong("active"),r.getLong("assignments"),r.getLong("resolved"),nullable(r,"average"),r.getLong("breached")));
        return new ReportDtos.Page<>(rows,total,page,(int)Math.ceil((double)total/size));
    }
    public void export(Scope s,String sort,String direction,RowCallbackHandler handler){
        IncidentSorting.sort(sort,direction);
        String column=switch(sort){case "id" -> "i.id";case "title" -> "i.title";case "status" -> "i.status";case "priority" -> "i.priority";case "updatedAt" -> "i.updated_at";case "resolvedAt" -> "i.resolved_at";case "service" -> "s.name";case "category" -> "c.name";case "technician" -> "u.display_name";case "responseDueAt" -> "i.response_due_at";case "resolutionDueAt" -> "i.resolution_due_at";default -> "i.created_at";};
        jdbc.query(s.cte()+"select i.id,i.title,i.status,i.priority,s.name service,c.name category,u.display_name technician,i.created_at,i.resolved_at,i.response_outcome,i.resolution_outcome,i.sla_status from selected i join it_services s on s.id=i.service_id join incident_categories c on c.id=i.category_id left join users u on u.id=i.technician_id order by "+column+" "+direction+",i.id "+direction,s.parameters(),handler);
    }
    private static Double nullable(java.sql.ResultSet r,String name)throws java.sql.SQLException {Number number=(Number)r.getObject(name);return number==null?null:number.doubleValue();}
}

