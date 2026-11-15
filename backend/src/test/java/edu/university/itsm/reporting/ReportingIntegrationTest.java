package edu.university.itsm.reporting;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.itsm.catalog.*;
import edu.university.itsm.identity.*;
import edu.university.itsm.incident.*;
import edu.university.itsm.incident.automation.SlaPolicyEntity;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.jwt-secret=reporting-integration-secret-at-least-32-bytes","spring.profiles.active=test","debug=false","app.sla.monitor-enabled=false","app.notifications.initial-delay-ms=3600000","spring.jpa.properties.hibernate.generate_statistics=true","logging.level.org.hibernate.stat=OFF","logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"})
@AutoConfigureMockMvc @Testcontainers @TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ReportingIntegrationTest {
    @Container static PostgreSQLContainer<?> db=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r){db.start();r.add("spring.datasource.url",db::getJdbcUrl);r.add("spring.datasource.username",db::getUsername);r.add("spring.datasource.password",db::getPassword);}
    @Autowired JdbcTemplate jdbc;@Autowired UserRepository users;@Autowired TechnicianProfileRepository profiles;@Autowired ITServiceRepository services;@Autowired IncidentCategoryRepository categories;@Autowired IncidentRepository incidents;@Autowired IncidentService application;@Autowired ReportService reports;@Autowired PasswordEncoder passwords;@Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired EntityManagerFactory emf;
    User employee,other,admin,t1,t2,t0;ITService a,b;IncidentCategory ca,cb;long tracking,breach,met,closed,unconfigured,approaching,verified;String adminToken,employeeToken,techToken;
    private final String password="ReportingPassword123!";
    private IncidentDtos.Search all(){return new IncidentDtos.Search(null,null,null,null,null,null,null,null);}
    @BeforeAll void setup()throws Exception{
        employee=user(Role.EMPLOYEE);other=user(Role.EMPLOYEE);admin=user(Role.ADMIN);t1=user(Role.TECHNICIAN);t2=user(Role.TECHNICIAN);t0=user(Role.TECHNICIAN);
        profiles.saveAndFlush(new TechnicianProfile(t1.getId(),5));profiles.saveAndFlush(new TechnicianProfile(t2.getId(),2));var zero=new TechnicianProfile(t0.getId(),3);zero.configure(3,false,Map.of());profiles.saveAndFlush(zero);
        a=services.saveAndFlush(new ITService("Campus network","",3));b=services.saveAndFlush(new ITService("Applications","",2));ca=categories.saveAndFlush(new IncidentCategory("Network",""));cb=categories.saveAndFlush(new IncidentCategory("Software",""));
        tracking=incident(employee,t1,a,ca,"Tracking",1,"ASSIGNED","P1");breach=incident(employee,t1,a,ca,"Resolution breached",2,"IN_PROGRESS","P2");met=incident(employee,t1,a,ca,"=SUM(1,2)\n\"quoted\"",3,"CLOSED","P3");closed=incident(other,t2,b,cb,"Closed breached",4,"CLOSED","P1");unconfigured=incident(other,null,b,cb,"No contract",5,"NEW",null);approaching=incident(employee,t2,b,cb,"Approaching",6,"ASSIGNED","P4");verified=incident(employee,t1,a,ca,"Verified",31,"VERIFIED","P2");
        Instant now=Instant.now();contract(tracking,now.plusSeconds(3600),now.plusSeconds(7200),null,null,3600,7200);
        Instant day=created(2);contract(breach,day.plusSeconds(600),day.plusSeconds(3600),day.plusSeconds(300),null,600,3600);
        for(long id:List.of(met,verified)){Instant start=id==met?created(3):created(31);contract(id,start.plusSeconds(300),start.plusSeconds(1800),start.plusSeconds(60),start.plusSeconds(600),300,1800);}
        day=created(4);contract(closed,day.plusSeconds(300),day.plusSeconds(900),day.plusSeconds(600),day.plusSeconds(1200),300,900);
        contract(approaching,now.plusSeconds(3600),now.plusSeconds(36000),null,null,36000,72000);
        jdbc.update("update incidents set feedback='Good',feedback_rating=5 where id=?",met);jdbc.update("update incidents set feedback='Slow',feedback_rating=3 where id=?",closed);
        adminToken=token(admin);employeeToken=token(employee);techToken=token(t1);
    }
    User user(Role role){return users.saveAndFlush(new User(role.name(),UUID.randomUUID()+"@uni.edu",passwords.encode(password),role));}
    Instant created(int day){return LocalDate.of(2026,1,day).atTime(day==31?23:12,day==31?59:0,day==31?59:0).toInstant(ZoneOffset.UTC);}
    long incident(User owner,User tech,ITService service,IncidentCategory category,String title,int day,String status,String priority){var value=incidents.saveAndFlush(new Incident(owner,title,"Reporting fixture",service,category,1,1));jdbc.update("update incidents set technician_id=?,status=?,priority=?,created_at=?,updated_at=?,resolution=?,verified_at=?,closed_at=? where id=?",tech==null?null:tech.getId(),status,priority,Timestamp.from(created(day)),Timestamp.from(created(day)),Set.of("RESOLVED","VERIFIED","CLOSED").contains(status)?"Fixture resolution":null,Set.of("VERIFIED","CLOSED").contains(status)?Timestamp.from(created(day).plusSeconds(1300)):null,status.equals("CLOSED")?Timestamp.from(created(day).plusSeconds(1400)):null,value.getId());return value.getId();}
    void contract(long id,Instant responseDue,Instant resolutionDue,Instant response,Instant resolution,int rs,int ss){jdbc.update("update incidents set response_due_at=?,resolution_due_at=?,first_response_at=?,resolved_at=?,sla_response_seconds=?,sla_resolution_seconds=?,sla_warning_percent=80 where id=?",Timestamp.from(responseDue),Timestamp.from(resolutionDue),response==null?null:Timestamp.from(response),resolution==null?null:Timestamp.from(resolution),rs,ss,id);}
    String token(User user)throws Exception{return json.readTree(mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("email",user.getEmail(),"password",password)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();}
    @Test void exactOverviewAndResolutionPerformance(){var s=reports.report(admin,all()).summary();assertEquals(7,s.total());assertEquals(4,s.open());assertEquals(3,s.resolved());assertEquals(1,s.critical());assertEquals(2,s.slaViolations());assertEquals(1,s.responseViolations());assertEquals(2,s.resolutionViolations());assertEquals(3,s.completedWithTargets());assertEquals(2,s.onTimeCompleted());assertEquals(200.0/3,s.compliancePercent(),.0001);assertEquals(800,s.averageResolutionSeconds());assertEquals(255,s.averageResponseSeconds());assertEquals(4,s.averageFeedback());}
    @Test void chartAggregationsReconcileWithTotals(){var r=reports.report(admin,all());for(var points:List.of(r.statuses(),r.priorities(),r.categories(),r.sla()))assertEquals(7,points.stream().mapToLong(ReportDtos.Bucket::count).sum());assertEquals(1,r.trends().size());assertEquals("2026-01",r.trends().getFirst().month());assertEquals(7,r.trends().getFirst().created());assertEquals(3,r.trends().getFirst().resolved());assertEquals(2,r.serviceHealth().stream().mapToLong(ReportDtos.Bucket::count).sum());}
    @Test void employeeAndTechnicianReportsRemainScoped(){assertEquals(5,reports.report(employee,all()).summary().total());assertEquals(4,reports.report(t1,all()).summary().total());var foreign=new IncidentDtos.Search(null,null,null,null,null,t2.getId(),null,null);assertEquals(0,reports.report(t1,foreign).summary().total());assertThrows(org.springframework.web.server.ResponseStatusException.class,() -> reports.technicians(employee,all(),0,10));assertThrows(org.springframework.web.server.ResponseStatusException.class,() -> reports.services(t1,all(),0,10));}
    @Test void liveWorkloadIgnoresCohortDatesAndIncludesIdleTechnicians(){var oneDay=new IncidentDtos.Search(null,null,null,null,null,null,LocalDate.of(2026,1,3),LocalDate.of(2026,1,3));var page=reports.technicians(admin,oneDay,0,10);assertEquals(3,page.totalElements());var first=page.content().stream().filter(t -> t.id().equals(t1.getId())).findFirst().orElseThrow();assertEquals(2,first.active());assertEquals(5,first.capacity());assertEquals(1,first.assignments());assertEquals(1,first.resolved());assertEquals(600,first.averageResolutionSeconds());var idle=page.content().stream().filter(t -> t.id().equals(t0.getId())).findFirst().orElseThrow();assertEquals(0,idle.active());assertFalse(idle.available());assertEquals(1,reports.technicians(t1,all(),0,10).totalElements());}
    @Test void technicianAndServiceAnalyticsArePagedInDatabase(){var first=reports.technicians(admin,all(),0,1);var second=reports.technicians(admin,all(),1,1);assertEquals(3,first.totalPages());assertEquals(t1.getId(),first.content().getFirst().id());assertEquals(t2.getId(),second.content().getFirst().id());var service=reports.services(admin,all(),0,1);assertEquals(2,service.totalElements());assertEquals(a.getId(),service.content().getFirst().id());assertEquals("CRITICAL",service.content().getFirst().health());assertEquals("DEGRADED",reports.services(admin,all(),1,1).content().getFirst().health());}
    @Test void allCombinedFiltersAndInclusiveUtcEndDateApplyToReportsAndLists()throws Exception{
        String query="id="+verified+"&status=VERIFIED&priority=P2&categoryId="+ca.getId()+"&serviceId="+a.getId()+"&technicianId="+t1.getId()+"&createdFrom=2026-01-31&createdTo=2026-01-31&slaStatus=MET";
        mvc.perform(get("/api/reports?"+query).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.summary.total").value(1));
        mvc.perform(get("/api/incidents?"+query).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(verified));
    }
    @Test void everySlaOutcomeFilterMatchesTheSameIncidentSetAcrossApis()throws Exception{
        Map<String,Long> expected=Map.of("TRACKING",1L,"APPROACHING",1L,"BREACHED",2L,"MET",2L,"UNCONFIGURED",1L);
        for(var entry:expected.entrySet())for(String api:List.of("/api/reports","/api/incidents")){var result=json.readTree(mvc.perform(get(api+"?slaStatus="+entry.getKey()).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());assertEquals(entry.getValue().longValue(),result.at(api.endsWith("reports")?"/summary/total":"/totalElements").asLong());}
    }
    @Test void deterministicSortAndPaginationPreserveAllRows()throws Exception{
        var ids=new ArrayList<Long>();for(int page=0;page<4;page++){var r=json.readTree(mvc.perform(get("/api/incidents?sort=id&direction=asc&page="+page+"&size=2").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(7)).andReturn().getResponse().getContentAsString());r.get("content").forEach(i -> ids.add(i.get("id").asLong()));}assertEquals(List.of(tracking,breach,met,closed,unconfigured,approaching,verified),ids);
    }
    @Test void tableFetchDoesNotIssuePerRowQueries(){var statistics=emf.unwrap(SessionFactory.class).getStatistics();statistics.clear();var page=application.search(admin,all(),0,2,"createdAt","desc");assertEquals(2,page.content().size());assertEquals(2,statistics.getPrepareStatementCount());}
    @Test void csvUsesFiltersSortUtcAndSafeQuotedCells()throws Exception{
        var response=mvc.perform(get("/api/reports/incidents.csv?createdFrom=2026-01-03&createdTo=2026-01-03&sort=id&direction=asc").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(header().string("Content-Disposition","attachment; filename=incidents.csv")).andReturn().getResponse();String csv=response.getContentAsString();assertTrue(response.getContentType().startsWith("text/csv"));assertTrue(csv.contains("\"'=SUM(1,2)\n\"\"quoted\"\"\""));assertTrue(csv.contains("2026-01-03T12:00:00Z"));assertFalse(csv.contains("Closed breached"));assertTrue(csv.endsWith("\r\n"));
    }
    @Test void csvAndReportsRejectPrivilegeEscalation()throws Exception{
        String csv=mvc.perform(get("/api/reports/incidents.csv").header("Authorization","Bearer "+employeeToken)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertFalse(csv.contains("Closed breached"));assertFalse(csv.contains("No contract"));assertTrue(csv.contains("Tracking"));
        mvc.perform(get("/api/reports/technicians").header("Authorization","Bearer "+employeeToken)).andExpect(status().isForbidden());mvc.perform(get("/api/reports/services").header("Authorization","Bearer "+techToken)).andExpect(status().isForbidden());for(String path:List.of("/api/reports","/api/reports/incidents.csv","/api/reports/technicians"))mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
    @Test void invalidFiltersSortAndPaginationAreRejected()throws Exception{
        for(String query:List.of("createdFrom=2026-02-02&createdTo=2026-01-01","slaStatus=FAKE","id=bad","priority=P0","technicianId=bad","createdTo=+999999999-12-31"))mvc.perform(get("/api/reports?"+query).header("Authorization","Bearer "+adminToken)).andExpect(status().isBadRequest());
        for(String query:List.of("sort=description;drop","direction=evil","size=101","page=-1"))mvc.perform(get("/api/incidents?"+query).header("Authorization","Bearer "+adminToken)).andExpect(status().isBadRequest());mvc.perform(get("/api/reports/incidents.csv?sort=bad").header("Authorization","Bearer "+adminToken)).andExpect(status().isBadRequest());mvc.perform(get("/api/reports/technicians?size=0").header("Authorization","Bearer "+adminToken)).andExpect(status().isBadRequest());
    }
    @Test void emptyReportHasNoFabricatedAveragesOrCompliance(){var empty=new IncidentDtos.Search(null,null,null,null,null,null,LocalDate.of(2025,1,1),LocalDate.of(2025,1,31));var r=reports.report(admin,empty);assertEquals(0,r.summary().total());assertNull(r.summary().averageResolutionSeconds());assertNull(r.summary().compliancePercent());assertTrue(r.trends().isEmpty());assertTrue(r.categories().isEmpty());}
    @Test void postgresSlaBoundariesMatchDomainOutcomes(){Instant start=Instant.parse("2026-01-01T00:00:00Z");var sla=new SlaSnapshot();sla.configure(new SlaPolicyEntity(null,IncidentPriority.P1,100,200,80,true),start);for(int seconds:List.of(0,79,80,100,101)){Instant now=start.plusSeconds(seconds);String sql=jdbc.queryForObject("select itsm_sla_outcome(?,?,null,100,80,?)",String.class,Timestamp.from(start.plusSeconds(100)),null,Timestamp.from(now));assertEquals(sla.responseOutcome(now).name(),sql);}assertEquals("BREACHED",jdbc.queryForObject("select itsm_sla_outcome(?,?,?,?,?,?)",String.class,Timestamp.from(start.plusSeconds(1000)),Timestamp.from(start.plusSeconds(50)),Timestamp.from(start.plusSeconds(100)),1000,80,Timestamp.from(start.plusSeconds(60))));}
}

