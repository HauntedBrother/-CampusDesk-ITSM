package edu.university.itsm.incident;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.itsm.catalog.*;
import edu.university.itsm.identity.*;
import edu.university.itsm.incident.automation.*;
import edu.university.itsm.intelligence.*;
import edu.university.itsm.notification.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.jwt-secret=phase-four-integration-secret-at-least-32-bytes","spring.profiles.active=test","debug=false","app.sla.monitor-enabled=false","app.notifications.initial-delay-ms=3600000"})
@AutoConfigureMockMvc @Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class Phase4IntegrationTest {
    @Container static PostgreSQLContainer<?> db=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",db::getJdbcUrl);r.add("spring.datasource.username",db::getUsername);r.add("spring.datasource.password",db::getPassword);}
    @Autowired IncidentService incidents;
    @Autowired IncidentRepository repository;
    @Autowired IncidentHistoryRepository history;
    @Autowired IncidentAutomation automation;
    @Autowired UserRepository users;
    @Autowired ITServiceRepository services;
    @Autowired IncidentCategoryRepository categories;
    @Autowired TechnicianProfileRepository profiles;
    @Autowired AutomationSettingsRepository settings;
    @Autowired SlaPolicyRepository slaPolicies;
    @Autowired IntelligenceService intelligence;
    @Autowired NotificationService notifications;
    @Autowired NotificationProcessor processor;
    @Autowired NotificationObserver observer;
    @Autowired NotificationOutboxRepository outbox;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @MockitoSpyBean SimulatedEmailChannel emailChannel;
    User employee,other,admin,technician;
    ITService service;
    IncidentCategory category;
    @BeforeEach void setup(){
        employee=account(Role.EMPLOYEE);other=account(Role.EMPLOYEE);admin=account(Role.ADMIN);technician=account(Role.TECHNICIAN);
        service=services.saveAndFlush(new ITService("Phase4 "+UUID.randomUUID(),"Network",3));
        category=categories.saveAndFlush(new IncidentCategory("Phase4 "+UUID.randomUUID(),"Connectivity"));
        tx.executeWithoutResult(t -> {var config=settings.findById(1).orElseThrow();config.configure(AssignmentMode.SKILL_BASED,false);settings.flush();var profile=new TechnicianProfile(technician.getId(),5);profile.configure(5,true,Map.of(category.getId(),3));profiles.saveAndFlush(profile);});
    }
    User account(Role role){return users.saveAndFlush(new User(role.name(),UUID.randomUUID()+"@uni.edu",passwords.encode("Phase4Password123!"),role));}
    IncidentDtos.Input input(){return new IncidentDtos.Input("Campus network failure","Cannot connect to campus wireless network in library",service.getId(),category.getId(),2,2);}
    long create(){return incidents.create(employee,input()).incident().id();}
    long count(String table){return jdbc.queryForObject("select count(*) from "+table,Long.class);}
    long count(String table,long incident){return jdbc.queryForObject("select count(*) from "+table+" where incident_id=?",Long.class,incident);}
    List<UUID> events(long id){return jdbc.queryForList("select id from notification_outbox where incident_id=?",UUID.class,id);}
    String token(User user)throws Exception{return json.readTree(mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("email",user.getEmail(),"password","Phase4Password123!")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();}
    @Test void deliveryOccursOnlyAfterCommitAndExactlyOnce(){
        long before=count("in_app_notifications");
        long id=tx.execute(t -> {long created=create();assertEquals(before,count("in_app_notifications"));assertFalse(events(created).isEmpty());return created;});
        long recipients=jdbc.queryForObject("select count(*) from notification_outbox_recipients r join notification_outbox e on e.id=r.event_id where e.incident_id=?",Long.class,id);
        assertTrue(recipients>0);assertEquals(recipients,count("in_app_notifications",id));assertEquals(recipients,count("simulated_email_deliveries",id));
        events(id).forEach(processor::process);events(id).forEach(processor::process);
        assertEquals(recipients,count("in_app_notifications",id));assertEquals(recipients,count("simulated_email_deliveries",id));
        assertEquals(0,jdbc.queryForObject("select count(*) from notification_outbox where incident_id=? and processed_at is null",Long.class,id));
    }
    @Test void rolledBackIncidentProducesNoHistoryOutboxOrDelivery(){
        var tables=List.of("incidents","incident_history","notification_outbox","in_app_notifications","simulated_email_deliveries");var before=tables.stream().map(this::count).toList();
        assertThrows(IllegalStateException.class,() -> tx.executeWithoutResult(t -> {create();throw new IllegalStateException("Rollback deliberately");}));
        assertEquals(before,tables.stream().map(this::count).toList());
    }
    @Test void channelFailureRollsBackPartialDeliveryAndRetriesSafely(){
        AtomicBoolean failed=new AtomicBoolean();
        doAnswer(call -> {NotificationOutbox e=call.getArgument(0);if(e.getAction().equals("CREATED") && failed.compareAndSet(false,true))throw new IllegalStateException("Simulated channel failure");return call.callRealMethod();}).when(emailChannel).deliver(any(),any());
        long id;
        try{id=create();}finally{doCallRealMethod().when(emailChannel).deliver(any(),any());}
        UUID pending=jdbc.queryForObject("select id from notification_outbox where incident_id=? and processed_at is null",UUID.class,id);
        assertEquals(1,outbox.findById(pending).orElseThrow().getAttempts());
        assertEquals(0,jdbc.queryForObject("select count(*) from in_app_notifications where event_id=?",Long.class,pending));
        processor.process(pending);processor.process(pending);
        long recipients=jdbc.queryForObject("select count(*) from notification_outbox_recipients where event_id=?",Long.class,pending);
        assertEquals(recipients,jdbc.queryForObject("select count(*) from in_app_notifications where event_id=?",Long.class,pending));
        assertEquals(recipients,jdbc.queryForObject("select count(*) from simulated_email_deliveries where event_id=?",Long.class,pending));
    }
    @Test void concurrentRetriesCannotDuplicateEitherChannel()throws Exception{
        long id=create();UUID event=events(id).getFirst();jdbc.update("update notification_outbox set processed_at=null where id=?",event);
        long messages=count("in_app_notifications",id),emails=count("simulated_email_deliveries",id);
        try(var pool=Executors.newFixedThreadPool(4)){var jobs=new ArrayList<Future<?>>();for(int i=0;i<4;i++)jobs.add(pool.submit(() -> processor.process(event)));for(var job:jobs)job.get(30,TimeUnit.SECONDS);}
        assertEquals(messages,count("in_app_notifications",id));assertEquals(emails,count("simulated_email_deliveries",id));
    }
    @Test void notificationPermissionsAndReadStateAreEnforcedOverHttp()throws Exception{
        create();var notification=notifications.list(employee,false,0,20).content().getFirst();String own=token(employee),foreign=token(other);
        mvc.perform(get("/api/notifications").header("Authorization","Bearer "+foreign)).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(post("/api/notifications/"+notification.id()+"/read").header("Authorization","Bearer "+foreign).contentType("application/json").content("{\"read\":true}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/notifications/"+notification.id()+"/read").header("Authorization","Bearer "+own).contentType("application/json").content("{\"read\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.readAt").isNotEmpty());
        var first=notifications.read(employee,notification.id(),true).readAt();assertEquals(first,notifications.read(employee,notification.id(),true).readAt());assertNull(notifications.read(employee,notification.id(),false).readAt());
        mvc.perform(get("/api/notifications/email-deliveries").header("Authorization","Bearer "+own)).andExpect(status().isForbidden());
        mvc.perform(get("/api/notifications/email-deliveries").header("Authorization","Bearer "+token(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].status").value("SIMULATED"));
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/notifications/"+notification.id()+"/read").header("Authorization","Bearer "+own).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
    }
    @Test void reassignmentRevokesOldTechnicianNotificationAccess(){
        long id=create();incidents.classify(admin,id);incidents.assign(admin,id,new IncidentDtos.Assignment(technician.getId()));
        var message=notifications.list(technician,false,0,100).content().getFirst();User replacement=account(Role.TECHNICIAN);
        var profile=new TechnicianProfile(replacement.getId(),5);profile.configure(5,true,Map.of(category.getId(),3));profiles.saveAndFlush(profile);
        incidents.assign(admin,id,new IncidentDtos.Assignment(replacement.getId()));
        assertTrue(notifications.list(technician,false,0,100).content().isEmpty());assertThrows(org.springframework.web.server.ResponseStatusException.class,() -> notifications.read(technician,message.id(),true));assertFalse(notifications.list(replacement,false,0,100).content().isEmpty());
    }
    @Test void duplicateMatchingUsesServiceActiveStateAndCurrentReadPermissions(){
        long own=create();long foreign=incidents.create(other,input()).incident().id();var another=services.saveAndFlush(new ITService("Other "+UUID.randomUUID(),"",1));
        long different=incidents.create(employee,new IncidentDtos.Input(input().title(),input().description(),another.getId(),category.getId(),1,1)).incident().id();
        var query=new IntelligenceService.Input(input().title(),input().description(),service.getId(),null);
        assertEquals(List.of(own),intelligence.suggest(employee,query).duplicates().stream().map(IntelligenceService.Duplicate::id).toList());
        assertEquals(2,intelligence.suggest(admin,query).duplicates().size());assertTrue(intelligence.suggest(technician,query).duplicates().isEmpty());
        assertTrue(intelligence.suggest(employee,new IntelligenceService.Input(input().title(),input().description(),service.getId(),own)).duplicates().isEmpty());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,() -> intelligence.suggest(employee,new IntelligenceService.Input(input().title(),input().description(),service.getId(),foreign)));
        tx.executeWithoutResult(t -> {Incident value=repository.findById(own).orElseThrow();value.transition(IncidentStatus.CLASSIFIED);value.assign(technician);value.transition(IncidentStatus.ACKNOWLEDGED);value.transition(IncidentStatus.IN_PROGRESS);value.resolve("Restored campus connectivity");repository.flush();});
        assertTrue(intelligence.suggest(employee,query).duplicates().isEmpty());assertTrue(repository.existsById(different));assertEquals(2,jdbc.queryForObject("select count(*) from incidents where service_id=?",Long.class,service.getId())); // both reports remain separate
    }
    @Test void rulesCategorizeWithoutBlockingManualCorrectionsAndNoMatchRequiresSelection(){
        String keyword="unique"+UUID.randomUUID().toString().replace("-","");intelligence.save(admin,new IntelligenceService.RuleInput(category.getId(),keyword,20,true));
        var auto=new IncidentDtos.Input(keyword,"Description with "+keyword,service.getId(),null,1,1);
        var created=incidents.create(employee,auto);assertEquals(category.getId(),created.incident().category().id());assertEquals("RULE",created.history().getFirst().newValues().get("categorySource"));
        var corrected=categories.saveAndFlush(new IncidentCategory("Correction "+UUID.randomUUID(),""));
        var result=incidents.update(employee,created.incident().id(),new IncidentDtos.Input(keyword,auto.description(),service.getId(),corrected.getId(),1,1));assertEquals(corrected.getId(),result.incident().category().id());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,() -> incidents.create(employee,new IncidentDtos.Input("Unknown","No matching category",service.getId(),null,1,1)));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,() -> intelligence.save(employee,new IntelligenceService.RuleInput(category.getId(),keyword,5,true)));
    }
    @Test void structuredAuditRecordsActualOldAndNewValuesAndRejectsMutation(){
        long id=create();incidents.update(employee,id,new IncidentDtos.Input("Corrected title",input().description(),service.getId(),category.getId(),3,3));
        incidents.classify(admin,id);incidents.assign(admin,id,new IncidentDtos.Assignment(technician.getId(),"Manual qualified assignment"));
        incidents.acknowledge(technician,id);incidents.start(technician,id);incidents.comment(technician,id,new IncidentDtos.Comment("Sensitive investigation note",true));
        incidents.resolve(technician,id,new IncidentDtos.Resolution("Replaced access point"));incidents.verify(employee,id);incidents.close(employee,id);incidents.feedback(employee,id,new IncidentDtos.Feedback(5,"Restored service"));
        var detail=incidents.get(admin,id);assertTrue(detail.history().stream().allMatch(h -> h.incidentId()==id && h.createdAt()!=null && !h.newValues().isEmpty()));
        var updated=detail.history().stream().filter(h -> h.action().equals("UPDATED")).findFirst().orElseThrow();assertEquals(input().title(),updated.oldValues().get("title"));assertEquals("Corrected title",updated.newValues().get("title"));assertEquals(employee.getId(),updated.actor().id());
        var assigned=detail.history().stream().filter(h -> h.action().equals("ASSIGNED")).findFirst().orElseThrow();assertNull(assigned.oldValues().get("technicianId"));assertEquals(technician.getId().toString(),assigned.newValues().get("technicianId"));
        assertTrue(detail.history().stream().anyMatch(h -> h.action().equals("PRIORITY_CALCULATED") && h.newValues().get("priority").equals("P1")));
        assertTrue(detail.history().stream().anyMatch(h -> h.action().equals("RESOLVED") && h.newValues().get("resolution").equals("Replaced access point")));
        assertTrue(incidents.get(employee,id).history().stream().noneMatch(h -> h.internal() || h.newValues().containsValue("Sensitive investigation note")));
        long auditId=updated.id();assertThrows(org.springframework.dao.DataAccessException.class,() -> jdbc.update("update incident_history set description='tampered' where id=?",auditId));assertThrows(org.springframework.dao.DataAccessException.class,() -> jdbc.update("delete from incident_history where id=?",auditId));
        assertEquals("Corrected title",incidents.get(admin,id).incident().title());assertThrows(org.springframework.web.server.ResponseStatusException.class,() -> incidents.get(other,id));
        Set<String> actions=new HashSet<>(jdbc.queryForList("select action from notification_outbox where incident_id=?",String.class,id));assertTrue(actions.containsAll(Set.of("CREATED","PRIORITY_CALCULATED","ASSIGNED","RESOLVED","CLOSE")));assertFalse(actions.contains("NOTE_ADDED"));
    }
    @Test void unchangedUpdatesAndAssignmentDoNotDuplicateAuditOrMessages(){
        long id=create();long rows=count("incident_history",id),messages=count("in_app_notifications",id);incidents.update(employee,id,input());assertEquals(rows,count("incident_history",id));assertEquals(messages,count("in_app_notifications",id));
        incidents.classify(admin,id);incidents.assign(admin,id,new IncidentDtos.Assignment(technician.getId()));rows=count("incident_history",id);incidents.assign(admin,id,new IncidentDtos.Assignment(technician.getId()));assertEquals(rows,count("incident_history",id));
    }
    @Test void scheduledRetryRecoversAnUnprocessedCommittedEvent(){long id=create();UUID event=events(id).getFirst();jdbc.update("update notification_outbox set processed_at=null,available_at=now()-interval '1 second' where id=?",event);long messages=count("in_app_notifications",id);observer.retryPending();assertNotNull(outbox.findById(event).orElseThrow().getProcessedAt());assertEquals(messages,count("in_app_notifications",id));}
    @Test void slaWarningsAndBreachesNotifyOnceAndAuditSystemActor(){
        long id=create();
        var contract=slaPolicies.saveAndFlush(new SlaPolicyEntity(service.getId(),IncidentPriority.P1,100,200,80,true));
        tx.executeWithoutResult(t -> {Incident incident=repository.findById(id).orElseThrow();var sla=incident.getSla();sla.configure(contract,java.time.Instant.now().minusSeconds(90));automation.evaluate(incident,null);repository.flush();});
        assertEquals(1,jdbc.queryForObject("select count(*) from notification_outbox where incident_id=? and action='SLA_RESPONSE_APPROACHING'",Long.class,id));
        contract.configure(100,200,81,true);slaPolicies.saveAndFlush(contract);
        tx.executeWithoutResult(t -> {Incident incident=repository.findById(id).orElseThrow();incident.getSla().configure(contract,java.time.Instant.now().minusSeconds(300));automation.evaluate(incident,null);automation.evaluate(incident,null);repository.flush();});
        for(String action:List.of("SLA_RESPONSE_BREACHED","SLA_RESOLUTION_BREACHED")){
            assertEquals(1,jdbc.queryForObject("select count(*) from notification_outbox where incident_id=? and action=?",Long.class,id,action));
            assertTrue(jdbc.queryForObject("select count(*) from in_app_notifications where incident_id=? and action=?",Long.class,id,action)>0);
        }
        var breaches=incidents.get(admin,id).history().stream().filter(h -> h.action().contains("BREACHED")).toList();assertEquals(2,breaches.size());assertTrue(breaches.stream().allMatch(h -> h.actor().id()==null && h.actor().displayName().equals("System") && h.newValues().get("responseBreachedAt")!=null));
    }
    @Test void administratorOverrideIsAuditedAndClosureNotified(){long id=create();incidents.classify(admin,id);incidents.assign(admin,id,new IncidentDtos.Assignment(technician.getId()));incidents.acknowledge(technician,id);incidents.start(technician,id);incidents.resolve(technician,id,new IncidentDtos.Resolution("Restored network connectivity"));var closed=incidents.override(admin,id,new IncidentDtos.Override(IncidentStatus.CLOSED,"Emergency closure approved by administrator"));var entry=closed.history().stream().filter(h -> h.action().equals("ADMIN_OVERRIDE")).findFirst().orElseThrow();assertEquals(admin.getId(),entry.actor().id());assertEquals("RESOLVED",entry.oldValues().get("status"));assertEquals("CLOSED",entry.newValues().get("status"));assertTrue(entry.description().contains("Emergency closure"));assertTrue(jdbc.queryForObject("select count(*) from in_app_notifications where incident_id=? and action='ADMIN_OVERRIDE'",Long.class,id)>0);}
}

