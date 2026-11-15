package edu.university.itsm.incident;

import edu.university.itsm.catalog.*;
import edu.university.itsm.identity.*;
import edu.university.itsm.incident.automation.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"app.jwt-secret=automation-integration-test-secret-at-least-32-bytes","spring.profiles.active=test","app.sla.monitor-enabled=false","debug=false"})
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class AutomationIntegrationTest {
    @Container static PostgreSQLContainer<?> db=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",db::getJdbcUrl);r.add("spring.datasource.username",db::getUsername);r.add("spring.datasource.password",db::getPassword);}
    @Autowired IncidentService incidents;
    @Autowired IncidentRepository repository;
    @Autowired IncidentAutomation automation;
    @Autowired AutomationConfigurationService configuration;
    @Autowired AutomationSettingsRepository settings;
    @Autowired SlaPolicyRepository policies;
    @Autowired UserRepository users;
    @Autowired TechnicianProfileRepository profiles;
    @Autowired ITServiceRepository services;
    @Autowired IncidentCategoryRepository categories;
    @Autowired IncidentHistoryRepository history;
    @Autowired ConfigurationAuditRepository configurationAudit;
    @Autowired TransactionTemplate transactions;
    @Autowired EntityManager entityManager;
    User employee,admin,expert,generalist;
    ITService service;
    IncidentCategory category;
    @BeforeEach void setup() {
        employee=user(Role.EMPLOYEE);admin=user(Role.ADMIN);expert=user(Role.TECHNICIAN);generalist=user(Role.TECHNICIAN);
        service=services.saveAndFlush(new ITService("Service "+UUID.randomUUID(),"",3));category=categories.saveAndFlush(new IncidentCategory("Category "+UUID.randomUUID(),""));
        profiles.saveAndFlush(new TechnicianProfile(expert.getId(),5));profiles.saveAndFlush(new TechnicianProfile(generalist.getId(),5));
        configuration.configure(admin,new AutomationDtos.Settings(AssignmentMode.SKILL_BASED,true));
        configure(expert,5,true,3);configure(generalist,5,true,1);
    }
    User user(Role role){return users.saveAndFlush(new User(role.name(),UUID.randomUUID()+"@uni.edu","not-used-by-service-tests",role));}
    void configure(User user,int capacity,boolean available,int skill){configuration.configureTechnician(admin,user.getId(),new AutomationDtos.TechnicianInput(capacity,available,skill==0 ? List.of() : List.of(new AutomationDtos.Skill(category.getId(),skill))));}
    IncidentDtos.Input input(int impact,int urgency){return new IncidentDtos.Input("Network down","Campus unavailable",service.getId(),category.getId(),impact,urgency);}
    @Test void creationPersistsPriorityQualifiedAssignmentAndDefaultSla() {
        var detail=incidents.create(employee,input(3,2));var i=detail.incident();
        assertEquals(IncidentPriority.P1,i.priority());assertEquals(18,i.priorityScore());assertEquals(expert.getId(),i.technician().id());assertEquals(IncidentStatus.ASSIGNED,i.status());
        assertEquals(i.createdAt().plusSeconds(300),i.sla().responseDueAt());assertEquals(i.createdAt().plusSeconds(1800),i.sla().resolutionDueAt());
        assertEquals(i.createdAt(),incidents.get(employee,i.id()).incident().createdAt());
        transactions.executeWithoutResult(tx -> {entityManager.clear();Incident stored=repository.findById(i.id()).orElseThrow();assertEquals(18,stored.getPriorityScore());assertNotNull(stored.getSla().getPolicyId());assertTrue(history.findByIncidentIdOrderByIdAsc(i.id()).stream().anyMatch(h -> h.getAction().equals("AUTO_ASSIGNED")));});
    }
    @Test void strategySwitchChangesSelectionWithoutRebalancingExistingWork() {
        var first=incidents.create(employee,input(3,2)).incident();assertEquals(expert.getId(),first.technician().id());
        configuration.configure(admin,new AutomationDtos.Settings(AssignmentMode.LOAD_BASED,true));
        var second=incidents.create(employee,input(3,2)).incident();assertEquals(generalist.getId(),second.technician().id());assertEquals(expert.getId(),incidents.get(admin,first.id()).incident().technician().id());
    }
    @Test void emptyPoolRetainsIncidentAndExplicitFailureReason() {
        configure(expert,5,false,3);configure(generalist,5,true,0);
        var i=incidents.create(employee,input(3,2)).incident();assertEquals(IncidentStatus.CLASSIFIED,i.status());assertNull(i.technician());assertNotNull(i.assignmentIssue());assertNotNull(i.sla().responseDueAt());
        assertEquals(IncidentStatus.CLASSIFIED,repository.findById(i.id()).orElseThrow().getStatus());
    }
    @Test void twoConcurrentReportsCannotOverbookOneAvailableSlot() throws Exception {
        configure(expert,1,true,3);configure(generalist,5,false,1);
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try(ExecutorService pool=Executors.newFixedThreadPool(2)) {
            Callable<IncidentDtos.Detail> create=() -> {ready.countDown();assertTrue(start.await(10,TimeUnit.SECONDS));return incidents.create(employee,input(3,2));};
            Future<IncidentDtos.Detail> one=pool.submit(create),two=pool.submit(create);assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();
            var results=List.of(one.get(30,TimeUnit.SECONDS),two.get(30,TimeUnit.SECONDS));
            assertEquals(1,results.stream().filter(d -> d.incident().status()==IncidentStatus.ASSIGNED).count());
            assertEquals(1,results.stream().filter(d -> d.incident().status()==IncidentStatus.CLASSIFIED).count());
            assertEquals(1,repository.countByTechnicianIdAndStatusIn(expert.getId(),AssignmentEngine.ACTIVE));
        }
    }
    @Test void serviceCriticalityChangeRecalculatesActivePriorityAndOriginalDeadline() {
        configuration.configure(admin,new AutomationDtos.Settings(AssignmentMode.SKILL_BASED,false));var original=incidents.create(employee,input(3,2)).incident();
        // Exercise the same catalog application service used by the admin endpoint.
        catalog.saveService(admin,service.getId(),new CatalogDtos.ServiceInput(service.getName(),"",1,true));
        var changed=incidents.get(employee,original.id()).incident();assertEquals(IncidentPriority.P3,changed.priority());assertEquals(6,changed.priorityScore());assertEquals(original.createdAt().plusSeconds(28800),changed.sla().resolutionDueAt());
    }
    @Autowired CatalogService catalog;
    @Test void serviceSpecificPolicyAndEditsPreserveOriginalClock() {
        var original=incidents.create(employee,input(3,2)).incident();
        configuration.savePolicy(admin,new AutomationDtos.PolicyInput(service.getId(),IncidentPriority.P1,120,600,75,true));
        var changed=incidents.get(employee,original.id()).incident();assertEquals(original.createdAt().plusSeconds(120),changed.sla().responseDueAt());assertEquals(original.createdAt().plusSeconds(600),changed.sla().resolutionDueAt());assertEquals(75,changed.sla().warningPercent());
        configuration.savePolicy(admin,new AutomationDtos.PolicyInput(service.getId(),IncidentPriority.P1,120,600,75,false));
        assertEquals(original.createdAt().plusSeconds(300),incidents.get(employee,original.id()).incident().sla().responseDueAt());
    }
    @Test void lateAcknowledgementAndRepeatedMonitorEvaluationPersistOneBreach() {
        var created=incidents.create(employee,input(3,2)).incident();
        transactions.executeWithoutResult(tx -> {
            automation.lock();Incident i=repository.lockById(created.id()).orElseThrow();Reflection.setCreated(i,Instant.now().minusSeconds(600));
            // Age the existing persisted contract; do not reference an unsaved policy UUID.
            org.springframework.test.util.ReflectionTestUtils.setField(i.getSla(),"responseDueAt",i.getCreatedAt().plusSeconds(300));
            org.springframework.test.util.ReflectionTestUtils.setField(i.getSla(),"resolutionDueAt",i.getCreatedAt().plusSeconds(1800));
        });
        incidents.acknowledge(expert,created.id());
        transactions.executeWithoutResult(tx -> {automation.lock();Incident i=repository.lockById(created.id()).orElseThrow();automation.evaluate(i,null);automation.evaluate(i,null);});
        var result=incidents.get(admin,created.id()).incident();assertEquals(SlaSnapshot.Outcome.BREACHED,result.sla().responseStatus());assertNotNull(result.sla().firstAcknowledgedAt());assertNotNull(result.sla().responseBreachedAt());
        assertEquals(1,history.findByIncidentIdOrderByIdAsc(created.id()).stream().filter(h -> h.getAction().equals("SLA_RESPONSE_BREACHED")).count());
    }
    @Test void missingPoliciesDoNotDiscardReport() {
        SlaPolicyEntity global=policies.findByServiceIdIsNullAndPriority(IncidentPriority.P1).orElseThrow();
        global.configure(300,1800,80,false);policies.saveAndFlush(global);
        try {var i=incidents.create(employee,input(3,2)).incident();assertNull(i.sla().responseDueAt());assertNotNull(i.sla().issue());assertTrue(repository.existsById(i.id()));}
        finally {global.configure(300,1800,80,true);policies.saveAndFlush(global);}
    }
    @Test void activeWorkProtectsCapacityAndRequiredSkillsAndManualOverrideIsAudited() {
        var i=incidents.create(employee,input(3,2)).incident();
        assertThrows(InvalidIncidentOperation.class,() -> configure(expert,5,true,0));
        incidents.assign(admin,i.id(),new IncidentDtos.Assignment(generalist.getId(),"Balance this incident across the team"));
        assertEquals(generalist.getId(),incidents.get(admin,i.id()).incident().technician().id());
        assertTrue(history.findByIncidentIdOrderByIdAsc(i.id()).stream().anyMatch(h -> h.getAction().equals("ASSIGNED") && h.getDescription().contains("Balance this incident")));
        assertTrue(configurationAudit.count()>0);
    }
    private static class Reflection {static void setCreated(Incident incident,Instant created){org.springframework.test.util.ReflectionTestUtils.setField(incident,"createdAt",created);}}
}
