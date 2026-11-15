package edu.university.itsm.incident;

import com.fasterxml.jackson.databind.*;
import edu.university.itsm.auth.*;
import edu.university.itsm.catalog.*;
import edu.university.itsm.identity.*;
import edu.university.itsm.security.SecurityConfig;
import edu.university.itsm.shared.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP validation and real security with mocked repositories; PostgreSQL tests cover persistence. */
@WebMvcTest(controllers={IncidentController.class,CatalogController.class},properties={"app.jwt-secret=incident-http-tests-secret-at-least-32-bytes","debug=false"})
@Import({SecurityConfig.class,CurrentUser.class,IncidentService.class,IncidentPolicy.class,CatalogService.class,ApiErrors.class,IncidentAudit.class})
class IncidentHttpTest {
    @MockitoBean UserRepository users;
    @MockitoBean SessionRepository sessions;
    @MockitoBean IncidentRepository incidents;
    @MockitoBean IncidentCommentRepository comments;
    @MockitoBean IncidentHistoryRepository history;
    @MockitoBean ITServiceRepository services;
    @MockitoBean IncidentCategoryRepository categories;
    @MockitoBean TechnicianProfileRepository profiles;
    @MockitoBean IncidentAutomation automation;
    @MockitoBean edu.university.itsm.notification.NotificationEvents notificationEvents;
    @MockitoBean edu.university.itsm.intelligence.IntelligenceService intelligence;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtEncoder encoder;
    User employee,other,technician,admin;
    Map<UUID,User> accounts;
    Map<UUID,AuthSession> activeSessions;
    String employeeToken,otherToken,technicianToken,adminToken;
    ITService service;
    IncidentCategory category;
    Incident incident;
    @BeforeEach void setup() {
        employee=new User("Employee","e@uni.edu","hash",Role.EMPLOYEE); other=new User("Other","o@uni.edu","hash",Role.EMPLOYEE);
        technician=new User("Technician","t@uni.edu","hash",Role.TECHNICIAN); admin=new User("Admin","a@uni.edu","hash",Role.ADMIN);
        accounts=new HashMap<>(); activeSessions=new HashMap<>();
        for(User u:List.of(employee,other,technician,admin)) accounts.put(u.getId(),u);
        when(users.findById(any())).thenAnswer(a -> Optional.ofNullable(accounts.get(a.getArgument(0))));
        when(sessions.findById(any())).thenAnswer(a -> Optional.ofNullable(activeSessions.get(a.getArgument(0))));
        employeeToken=token(employee); otherToken=token(other); technicianToken=token(technician); adminToken=token(admin);
        service=new ITService("Wi-Fi","Campus Wi-Fi",3); category=new IncidentCategory("Network","Connectivity");
        when(services.findById(service.getId())).thenReturn(Optional.of(service)); when(categories.findById(category.getId())).thenReturn(Optional.of(category));
        incident=new Incident(employee,"Wi-Fi down","Library network unavailable",service,category,3,2); ReflectionTestUtils.setField(incident,"id",1L);
        when(incidents.findById(1L)).thenReturn(Optional.of(incident)); when(incidents.lockById(1L)).thenReturn(Optional.of(incident));
        when(incidents.saveAndFlush(any())).thenAnswer(a -> { Incident i=a.getArgument(0); ReflectionTestUtils.setField(i,"id",2L); return i; });
    }
    String token(User actor) {
        AuthSession session=new AuthSession(actor.getId(),Instant.now().plusSeconds(3600)); activeSessions.put(session.getId(),session);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),JwtClaimsSet.builder()
            .issuer("university-itsm").subject(actor.getId().toString()).id(session.getId().toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build())).getTokenValue();
    }
    Map<String,Object> input() { return Map.of("title","Library Wi-Fi","description","Cannot connect","serviceId",service.getId(),"categoryId",category.getId(),"impact",2,"urgency",3); }
    @Test void anonymousCannotAccessIncidentApi() throws Exception { mvc.perform(get("/api/incidents/1")).andExpect(status().isUnauthorized()); }
    @Test void employeeCreatesNewIncidentWithNoCalculatedPriority() throws Exception {
        mvc.perform(post("/api/incidents").header("Authorization","Bearer "+employeeToken).contentType("application/json").content(json.writeValueAsString(input())))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.incident.status").value("NEW")).andExpect(jsonPath("$.incident.priority").doesNotExist());
    }
    @Test void adminCannotUseEmployeeCreationApi() throws Exception {
        mvc.perform(post("/api/incidents").header("Authorization","Bearer "+adminToken).contentType("application/json").content(json.writeValueAsString(input()))).andExpect(status().isForbidden());
    }
    @Test void employeeCannotClassify() throws Exception { mvc.perform(post("/api/incidents/1/classify").header("Authorization","Bearer "+employeeToken)).andExpect(status().isForbidden()); }
    @Test void otherEmployeeCannotReadOrAddComments() throws Exception {
        mvc.perform(get("/api/incidents/1").header("Authorization","Bearer "+otherToken)).andExpect(status().isNotFound());
        mvc.perform(post("/api/incidents/1/comments").header("Authorization","Bearer "+otherToken).contentType("application/json").content("{\"body\":\"Unauthorized\",\"internal\":false}")).andExpect(status().isNotFound());
    }
    @Test void assignedTechnicianCanAcknowledgeButCannotVerify() throws Exception {
        incident.transition(IncidentStatus.CLASSIFIED); incident.assign(technician);
        mvc.perform(post("/api/incidents/1/acknowledge").header("Authorization","Bearer "+technicianToken)).andExpect(status().isOk()).andExpect(jsonPath("$.incident.status").value("ACKNOWLEDGED"));
        mvc.perform(post("/api/incidents/1/verify").header("Authorization","Bearer "+technicianToken)).andExpect(status().isForbidden());
    }
    @Test void inputScoresAndRequiredFieldsValidated() throws Exception {
        var body=new HashMap<>(input()); body.put("impact",4); body.put("title","");
        mvc.perform(post("/api/incidents").header("Authorization","Bearer "+employeeToken).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
        verify(incidents,never()).saveAndFlush(any());
    }
    @Test void statusInjectionCannotChangeLifecycle() throws Exception {
        var body=new HashMap<>(input()); body.put("status","CLOSED"); body.put("technicianId",technician.getId()); body.put("priority","P1");
        mvc.perform(put("/api/incidents/1").header("Authorization","Bearer "+employeeToken).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.incident.status").value("NEW")).andExpect(jsonPath("$.incident.technician").doesNotExist()).andExpect(jsonPath("$.incident.priority").doesNotExist());
    }
    @Test void adminOverridesRequireReasonAndExistingPrerequisites() throws Exception {
        mvc.perform(post("/api/incidents/1/override").header("Authorization","Bearer "+adminToken).contentType("application/json").content("{\"target\":\"CLOSED\",\"reason\":\"short\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/incidents/1/override").header("Authorization","Bearer "+adminToken).contentType("application/json").content("{\"target\":\"CLOSED\",\"reason\":\"Exceptional closure requested\"}")).andExpect(status().isConflict());
    }
    @ParameterizedTest @CsvSource({"status,BOGUS","priority,P9","id,INC-invalid","categoryId,not-a-uuid","createdFrom,not-a-date","size,101"})
    void invalidFiltersRejected(String name,String value) throws Exception {
        mvc.perform(get("/api/incidents").param(name,value).header("Authorization","Bearer "+employeeToken)).andExpect(status().isBadRequest());
    }
    @Test void catalogWritesAndInactiveListingAreAdminOnly() throws Exception {
        mvc.perform(post("/api/catalog/categories").header("Authorization","Bearer "+employeeToken).contentType("application/json").content("{\"name\":\"Network\",\"description\":\"\",\"active\":true}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/catalog/services").param("includeInactive","true").header("Authorization","Bearer "+employeeToken)).andExpect(status().isForbidden());
    }
}
