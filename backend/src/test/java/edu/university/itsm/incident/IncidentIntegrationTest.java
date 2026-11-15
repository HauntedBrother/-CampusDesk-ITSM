package edu.university.itsm.incident;

import com.fasterxml.jackson.databind.*;
import edu.university.itsm.catalog.*;
import edu.university.itsm.identity.*;
import edu.university.itsm.incident.automation.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.jwt-secret=incident-integration-test-secret-at-least-32-bytes","spring.profiles.active=test","debug=false","app.sla.monitor-enabled=false"})
@AutoConfigureMockMvc @Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class IncidentIntegrationTest {
    @Container static PostgreSQLContainer<?> db=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",db::getJdbcUrl); r.add("spring.datasource.username",db::getUsername); r.add("spring.datasource.password",db::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired TechnicianProfileRepository profiles;
    @Autowired ITServiceRepository services;
    @Autowired IncidentCategoryRepository categories;
    @Autowired IncidentRepository incidents;
    @Autowired IncidentHistoryRepository history;
    @Autowired PasswordEncoder passwords;
    @Autowired TransactionTemplate transactions;
    @Autowired EntityManager entityManager;
    @Autowired AutomationSettingsRepository automationSettings;
    private static final String PASSWORD="SecureIncidentPassword123!";
    String employeeToken,otherToken,technicianToken,adminToken;
    User technician;
    ITService service;
    IncidentCategory category;
    @BeforeEach void setup() throws Exception {
        employeeToken=account(Role.EMPLOYEE); otherToken=account(Role.EMPLOYEE);
        adminToken=account(Role.ADMIN); technicianToken=account(Role.TECHNICIAN);
        service=services.saveAndFlush(new ITService("Network "+UUID.randomUUID(),"Campus network",3));
        category=categories.saveAndFlush(new IncidentCategory("Connectivity "+UUID.randomUUID(),"Connection failures"));
        AutomationSettings settings=automationSettings.findById(1).orElseThrow(); settings.configure(AssignmentMode.SKILL_BASED,false); automationSettings.saveAndFlush(settings);
        transactions.executeWithoutResult(tx -> {
            TechnicianProfile profile=profiles.findById(technician.getId()).orElseThrow();
            profile.configure(5,true,Map.of(category.getId(),3)); profiles.flush();
        });
    }
    String account(Role role) throws Exception {
        User user=users.saveAndFlush(new User(role.name(),UUID.randomUUID()+"@uni.edu",passwords.encode(PASSWORD),role));
        if(role==Role.TECHNICIAN) { technician=user; profiles.saveAndFlush(new TechnicianProfile(user.getId(),5)); }
        return json.readTree(mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("email",user.getEmail(),"password",PASSWORD))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }
    Map<String,Object> input() { return Map.of("title","Wi-Fi down","description","Cannot connect in library","serviceId",service.getId(),"categoryId",category.getId(),"impact",3,"urgency",2); }
    JsonNode create() throws Exception {
        return json.readTree(mvc.perform(post("/api/incidents").header("Authorization","Bearer "+employeeToken).contentType("application/json").content(json.writeValueAsString(input())))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.incident.priority").value("P1")).andReturn().getResponse().getContentAsString());
    }
    JsonNode command(long id,String action,String token,Object body,int expected) throws Exception {
        var request=post("/api/incidents/"+id+"/"+action).header("Authorization","Bearer "+token);
        if(body!=null) request.contentType("application/json").content(json.writeValueAsString(body));
        String result=mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return result.isBlank() ? json.nullNode() : json.readTree(result);
    }
    void assign(long id) throws Exception { command(id,"classify",adminToken,null,200); command(id,"assign",adminToken,Map.of("technicianId",technician.getId()),200); }
    void resolve(long id) throws Exception {
        assign(id); command(id,"acknowledge",technicianToken,null,200); command(id,"start",technicianToken,null,200);
        command(id,"resolve",technicianToken,Map.of("resolution","Replaced failed access point"),200);
    }
    @Test void completeWorkflowPersistsEveryMilestoneCommentFeedbackAndHistory() throws Exception {
        long id=create().at("/incident/id").asLong(); assign(id);
        command(id,"acknowledge",technicianToken,null,200); command(id,"start",technicianToken,null,200);
        command(id,"comments",technicianToken,Map.of("body","Please retry from the library","internal",false),200);
        command(id,"comments",employeeToken,Map.of("body","Connection works now","internal",false),200);
        command(id,"resolve",technicianToken,Map.of("resolution","Replaced failed access point"),200);
        command(id,"verify",employeeToken,null,200); command(id,"close",employeeToken,null,200);
        command(id,"feedback",employeeToken,Map.of("rating",5,"feedback","Fast support"),200);
        transactions.executeWithoutResult(tx -> { entityManager.clear(); Incident stored=incidents.findById(id).orElseThrow();
            assertEquals(IncidentStatus.CLOSED,stored.getStatus()); assertEquals("Fast support",stored.getFeedback());
            assertNotNull(stored.getAcknowledgedAt()); assertNotNull(stored.getResolvedAt()); assertNotNull(stored.getVerifiedAt()); assertNotNull(stored.getClosedAt());
            assertTrue(history.findByIncidentIdOrderByIdAsc(id).size()>=11);
        });
        mvc.perform(get("/api/incidents/"+id).header("Authorization","Bearer "+employeeToken)).andExpect(status().isOk()).andExpect(jsonPath("$.comments.length()").value(2));
    }
    @Test void anotherEmployeeAndUnassignedTechnicianCannotReadOrMutate() throws Exception {
        long id=create().at("/incident/id").asLong();
        for(String token:List.of(otherToken,technicianToken)) {
            mvc.perform(get("/api/incidents/"+id).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
            command(id,"comments",token,Map.of("body","Unauthorized","internal",false),404);
            mvc.perform(get("/api/incidents").param("id",String.valueOf(id)).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        }
    }
    @Test void internalNotesAndTheirHistoryNeverReachEmployee() throws Exception {
        long id=create().at("/incident/id").asLong(); assign(id);
        command(id,"comments",technicianToken,Map.of("body","Private diagnostic output","internal",true),200);
        String employee=mvc.perform(get("/api/incidents/"+id).header("Authorization","Bearer "+employeeToken)).andExpect(status().isOk()).andExpect(jsonPath("$.comments.length()").value(0)).andReturn().getResponse().getContentAsString();
        assertFalse(employee.contains("Private diagnostic")); assertFalse(employee.contains("NOTE_ADDED"));
        mvc.perform(get("/api/incidents/"+id).header("Authorization","Bearer "+technicianToken)).andExpect(jsonPath("$.comments[0].internal").value(true));
        command(id,"comments",employeeToken,Map.of("body","Try hidden note","internal",true),403);
    }
    @Test void normalClosureCannotSkipVerificationAndOverrideIsAudited() throws Exception {
        long id=create().at("/incident/id").asLong(); resolve(id);
        command(id,"close",adminToken,null,409); command(id,"verify",technicianToken,null,403);
        JsonNode detail=command(id,"override",adminToken,Map.of("target","CLOSED","reason","Manager approved exceptional closure"),200);
        assertEquals("CLOSED",detail.at("/incident/status").asText());
        JsonNode last=detail.get("history").get(detail.get("history").size()-1);
        assertEquals("ADMIN_OVERRIDE",last.get("action").asText()); assertTrue(last.get("description").asText().contains("Manager approved"));
    }
    @Test void validatesInputsAndDoesNotOfferArbitraryStatusMutation() throws Exception {
        var invalid=new HashMap<>(input()); invalid.put("impact",4); invalid.put("title","");
        mvc.perform(post("/api/incidents").header("Authorization","Bearer "+employeeToken).contentType("application/json").content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());
        long id=create().at("/incident/id").asLong();
        mvc.perform(patch("/api/incidents/"+id).header("Authorization","Bearer "+employeeToken).contentType("application/json").content("{\"status\":\"CLOSED\"}")).andExpect(status().isMethodNotAllowed());
        command(id,"override",adminToken,Map.of("target","CLOSED","reason","short"),400);
        command(id,"classify",employeeToken,null,403);
    }
    @Test void combinedFiltersRespectOwnershipAndCalculatedPriority() throws Exception {
        long id=create().at("/incident/id").asLong(); assign(id);
        String today=LocalDate.now(ZoneOffset.UTC).toString();
        mvc.perform(get("/api/incidents").header("Authorization","Bearer "+employeeToken)
            .param("id","INC-%06d".formatted(id)).param("status","ASSIGNED").param("categoryId",category.getId().toString())
            .param("serviceId",service.getId().toString()).param("priority","P1").param("technicianId",technician.getId().toString())
            .param("createdFrom",today).param("createdTo",today)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/incidents").header("Authorization","Bearer "+employeeToken).param("id",String.valueOf(id)).param("priority","P4"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/incidents").header("Authorization","Bearer "+employeeToken).param("createdFrom","2026-11-20").param("createdTo","2026-11-01")).andExpect(status().isBadRequest());
    }
    @Test void onlyAdminCanManageCatalogAndDeactivationPreservesExistingIncident() throws Exception {
        long id=create().at("/incident/id").asLong();
        var catalogInput=Map.of("name",service.getName(),"description","Retired service","businessCriticality",3,"active",false);
        mvc.perform(put("/api/catalog/services/"+service.getId()).header("Authorization","Bearer "+employeeToken).contentType("application/json").content(json.writeValueAsString(catalogInput))).andExpect(status().isForbidden());
        mvc.perform(put("/api/catalog/services/"+service.getId()).header("Authorization","Bearer "+adminToken).contentType("application/json").content(json.writeValueAsString(catalogInput))).andExpect(status().isOk());
        mvc.perform(get("/api/incidents/"+id).header("Authorization","Bearer "+employeeToken)).andExpect(status().isOk()).andExpect(jsonPath("$.incident.service.active").value(false));
        mvc.perform(post("/api/incidents").header("Authorization","Bearer "+employeeToken).contentType("application/json").content(json.writeValueAsString(input()))).andExpect(status().isBadRequest());
    }
    @Test void assignmentCapacityAndReassignmentAreEnforcedByDatabaseBackedService() throws Exception {
        long first=create().at("/incident/id").asLong();
        // Replace capacity using SQL in the test transaction to exercise configuration without adding a Phase 2 provisioning API.
        transactions.executeWithoutResult(tx -> entityManager.createNativeQuery("update technician_profiles set capacity=1 where user_id=:id").setParameter("id",technician.getId()).executeUpdate());
        assign(first); long second=create().at("/incident/id").asLong(); command(second,"classify",adminToken,null,200);
        command(second,"assign",adminToken,Map.of("technicianId",technician.getId()),409);
        command(first,"acknowledge",technicianToken,null,200); command(first,"assign",adminToken,Map.of("technicianId",technician.getId()),409);
    }
    @Test void updatesPersistBeforeAssignmentAndRejectLaterEmployeeEdits() throws Exception {
        long id=create().at("/incident/id").asLong(); var changed=new HashMap<>(input()); changed.put("title","Library Wi-Fi outage");
        mvc.perform(put("/api/incidents/"+id).header("Authorization","Bearer "+employeeToken).contentType("application/json").content(json.writeValueAsString(changed)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.incident.title").value("Library Wi-Fi outage"));
        assertEquals("Library Wi-Fi outage",incidents.findById(id).orElseThrow().getTitle()); assign(id);
        mvc.perform(put("/api/incidents/"+id).header("Authorization","Bearer "+employeeToken).contentType("application/json").content(json.writeValueAsString(changed))).andExpect(status().isConflict());
    }
}
