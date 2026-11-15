package edu.university.itsm.incident;

import com.fasterxml.jackson.databind.*;
import edu.university.itsm.auth.*;
import edu.university.itsm.catalog.*;
import edu.university.itsm.identity.*;
import edu.university.itsm.incident.automation.*;
import edu.university.itsm.security.SecurityConfig;
import edu.university.itsm.shared.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers={IncidentController.class,AutomationController.class,CatalogController.class},properties={"app.jwt-secret=automation-http-tests-secret-at-least-32-bytes","debug=false"})
@Import({SecurityConfig.class,CurrentUser.class,IncidentService.class,IncidentPolicy.class,CatalogService.class,ApiErrors.class,
    IncidentAutomation.class,AutomationLock.class,MultiplicativePriorityPolicy.class,CalendarSlaPolicy.class,AssignmentEngine.class,
    SkillBasedAssignment.class,LoadBasedAssignment.class,AutomationConfigurationService.class,AutomationHttpTest.TimeConfig.class,IncidentAudit.class})
class AutomationHttpTest {
    static final Instant NOW=Instant.now();
    @TestConfiguration static class TimeConfig { @Bean Clock testClock(){return Clock.fixed(NOW,ZoneOffset.UTC);} }
    @MockitoBean UserRepository users;
    @MockitoBean SessionRepository sessions;
    @MockitoBean IncidentRepository incidents;
    @MockitoBean IncidentCommentRepository comments;
    @MockitoBean IncidentHistoryRepository history;
    @MockitoBean ITServiceRepository services;
    @MockitoBean IncidentCategoryRepository categories;
    @MockitoBean TechnicianProfileRepository profiles;
    @MockitoBean AutomationSettingsRepository settings;
    @MockitoBean SlaPolicyRepository policies;
    @MockitoBean ConfigurationAuditRepository configurationAudit;
    @MockitoBean edu.university.itsm.notification.NotificationEvents notificationEvents;
    @MockitoBean edu.university.itsm.intelligence.IntelligenceService intelligence;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtEncoder encoder;
    @Autowired IncidentAutomation automation;
    User employee,admin,tech1,tech2;
    String employeeToken,adminToken,technicianToken;
    Map<UUID,User> accounts;
    Map<UUID,AuthSession> activeSessions;
    Map<UUID,TechnicianProfile> profileMap;
    Map<UUID,Long> workloads;
    Map<IncidentPriority,SlaPolicyEntity> policyMap;
    Map<Long,Incident> records;
    AutomationSettings config;
    ITService service;
    IncidentCategory category;
    @BeforeEach void setup() {
        employee=new User("Employee","e@uni.edu","hash",Role.EMPLOYEE); admin=new User("Admin","a@uni.edu","hash",Role.ADMIN);
        tech1=new User("Expert","t1@uni.edu","hash",Role.TECHNICIAN); tech2=new User("Generalist","t2@uni.edu","hash",Role.TECHNICIAN);
        accounts=new HashMap<>(); activeSessions=new HashMap<>(); profileMap=new HashMap<>(); workloads=new HashMap<>(); policyMap=new EnumMap<>(IncidentPriority.class); records=new HashMap<>();
        for(User u:List.of(employee,admin,tech1,tech2)) accounts.put(u.getId(),u);
        when(users.findById(any())).thenAnswer(a -> Optional.ofNullable(accounts.get(a.getArgument(0))));
        when(users.findByRoleAndEnabledTrueOrderByDisplayNameAsc(Role.TECHNICIAN)).thenReturn(List.of(tech1,tech2));
        when(sessions.findById(any())).thenAnswer(a -> Optional.ofNullable(activeSessions.get(a.getArgument(0))));
        employeeToken=token(employee); adminToken=token(admin); technicianToken=token(tech1);
        config=new AutomationSettings(); when(settings.lockSettings()).thenReturn(Optional.of(config)); when(settings.findById(1)).thenReturn(Optional.of(config));
        service=new ITService("Campus network","",3); category=new IncidentCategory("Network","");
        when(services.findById(service.getId())).thenReturn(Optional.of(service)); when(services.existsById(service.getId())).thenReturn(true);
        when(categories.findById(category.getId())).thenReturn(Optional.of(category)); when(categories.existsById(category.getId())).thenReturn(true);
        for(User tech:List.of(tech1,tech2)) {
            TechnicianProfile profile=new TechnicianProfile(tech.getId(),5); profile.configure(5,true,Map.of(category.getId(),tech==tech1 ? 3 : 1)); profileMap.put(tech.getId(),profile);
        }
        workloads.put(tech1.getId(),3L); workloads.put(tech2.getId(),0L);
        when(profiles.findById(any())).thenAnswer(a -> Optional.ofNullable(profileMap.get(a.getArgument(0))));
        when(profiles.lockById(any())).thenAnswer(a -> Optional.ofNullable(profileMap.get(a.getArgument(0))));
        when(incidents.countByTechnicianIdAndStatusIn(any(),anyCollection())).thenAnswer(a -> workloads.getOrDefault(a.getArgument(0),0L));
        when(incidents.findById(anyLong())).thenAnswer(a -> Optional.ofNullable(records.get(a.getArgument(0))));
        when(incidents.lockById(anyLong())).thenAnswer(a -> Optional.ofNullable(records.get(a.getArgument(0))));
        when(incidents.saveAndFlush(any())).thenAnswer(a -> {Incident i=a.getArgument(0); ReflectionTestUtils.setField(i,"id",42L); records.put(42L,i); return i;});
        policyMap.put(IncidentPriority.P1,new SlaPolicyEntity(null,IncidentPriority.P1,300,1800,80,true));
        policyMap.put(IncidentPriority.P2,new SlaPolicyEntity(null,IncidentPriority.P2,900,7200,80,true));
        policyMap.put(IncidentPriority.P3,new SlaPolicyEntity(null,IncidentPriority.P3,3600,28800,80,true));
        policyMap.put(IncidentPriority.P4,new SlaPolicyEntity(null,IncidentPriority.P4,14400,86400,80,true));
        when(policies.findByServiceIdIsNullAndPriorityAndActiveTrue(any())).thenAnswer(a -> Optional.ofNullable(policyMap.get(a.getArgument(0))));
    }
    String token(User user) {
        AuthSession session=new AuthSession(user.getId(),Instant.now().plusSeconds(3600)); activeSessions.put(session.getId(),session);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),JwtClaimsSet.builder().issuer("university-itsm").subject(user.getId().toString()).id(session.getId().toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build())).getTokenValue();
    }
    Map<String,Object> input(int impact,int urgency){return Map.of("title","Network down","description","Cannot connect","serviceId",service.getId(),"categoryId",category.getId(),"impact",impact,"urgency",urgency);}
    JsonNode create() throws Exception {
        return json.readTree(mvc.perform(post("/api/incidents").header("Authorization","Bearer "+employeeToken).contentType("application/json").content(json.writeValueAsString(input(3,2))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    @Test void creationCalculatesPrioritySlaAndQualifiedSkillAssignment() throws Exception {
        JsonNode created=create().get("incident"); assertEquals("P1",created.get("priority").asText()); assertEquals(18,created.get("priorityScore").asInt());
        assertEquals("ASSIGNED",created.get("status").asText()); assertEquals(tech1.getId().toString(),created.at("/technician/id").asText());
        assertEquals(Instant.parse(created.get("createdAt").asText()).plusSeconds(300),Instant.parse(created.at("/sla/responseDueAt").asText()));
        verify(history,atLeastOnce()).saveAndFlush(any());
    }
    @Test void loadStrategySelectsLowestQualifiedWorkload() throws Exception {config.configure(AssignmentMode.LOAD_BASED,true);assertEquals(tech2.getId().toString(),create().at("/incident/technician/id").asText());}
    @Test void emptyPoolRetainsValidClassifiedIncidentAndVisibleReason() throws Exception {
        for(var p:profileMap.values()) p.configure(5,false,p.getSkills());
        JsonNode created=create().get("incident"); assertEquals("CLASSIFIED",created.get("status").asText()); assertTrue(created.get("technician").isNull()); assertTrue(created.get("assignmentIssue").asText().contains("No available"));
    }
    @Test void capacityAndQualificationAreEnforcedForManualOverrides() throws Exception {
        config.configure(AssignmentMode.SKILL_BASED,false); create();
        mvc.perform(post("/api/incidents/42/classify").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk());
        workloads.put(tech1.getId(),5L);
        mvc.perform(post("/api/incidents/42/assign").header("Authorization","Bearer "+adminToken).contentType("application/json").content(json.writeValueAsString(Map.of("technicianId",tech1.getId())))).andExpect(status().isConflict());
        profileMap.get(tech1.getId()).configure(5,true,Map.of()); workloads.put(tech1.getId(),0L);
        mvc.perform(post("/api/incidents/42/assign").header("Authorization","Bearer "+adminToken).contentType("application/json").content(json.writeValueAsString(Map.of("technicianId",tech1.getId())))).andExpect(status().isConflict());
    }
    @Test void changingFactorsRecalculatesFromOriginalCreationTime() throws Exception {
        config.configure(AssignmentMode.SKILL_BASED,false); JsonNode before=create().get("incident");
        JsonNode after=json.readTree(mvc.perform(put("/api/incidents/42").header("Authorization","Bearer "+employeeToken).contentType("application/json").content(json.writeValueAsString(input(1,1))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.incident.priority").value("P4")).andReturn().getResponse().getContentAsString()).get("incident");
        assertEquals(before.get("createdAt"),after.get("createdAt"));assertEquals(Instant.parse(before.get("createdAt").asText()).plusSeconds(86400),Instant.parse(after.at("/sla/resolutionDueAt").asText()));
    }
    @Test void missingPolicyIsVisibleWithoutRejectingReport() throws Exception {policyMap.clear();assertTrue(create().at("/incident/sla/issue").asText().contains("No active SLA policy"));}
    @Test void policyAndTechnicianConfigurationAreAdminOnly() throws Exception {
        mvc.perform(get("/api/admin/automation/settings").header("Authorization","Bearer "+employeeToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/automation/technicians").header("Authorization","Bearer "+technicianToken)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/automation/settings").header("Authorization","Bearer "+adminToken).contentType("application/json").content("{\"strategy\":\"LOAD_BASED\",\"automaticAssignment\":false}")).andExpect(status().isOk()).andExpect(jsonPath("$.strategy").value("LOAD_BASED"));
        verify(configurationAudit).save(any());
    }
    @Test void invalidSlaTargetsRejected() throws Exception {
        mvc.perform(put("/api/admin/automation/policies").header("Authorization","Bearer "+adminToken).contentType("application/json").content("{\"priority\":\"P1\",\"responseSeconds\":300,\"resolutionSeconds\":100,\"warningPercent\":80,\"active\":true}")).andExpect(status().isBadRequest());
    }
    @Test void latePendingSlaCreatesOneViolationRecordPerTarget() throws Exception {
        create(); Incident incident=records.get(42L); ReflectionTestUtils.setField(incident,"createdAt",NOW.minusSeconds(2000));
        incident.getSla().configure(new SlaPolicyEntity(null,IncidentPriority.P1,300,1800,80,true),incident.getCreatedAt());
        clearInvocations(history); automation.evaluate(incident,null); automation.evaluate(incident,null);
        var captured=org.mockito.ArgumentCaptor.forClass(IncidentHistory.class);verify(history,times(2)).saveAndFlush(captured.capture());
        assertTrue(captured.getAllValues().stream().allMatch(h -> h.getAction().endsWith("BREACHED") && h.getActor()==null));
    }
    @Test void explicitAssignmentCanRunWhileAutomaticModeIsDisabled() throws Exception {
        config.configure(AssignmentMode.SKILL_BASED,false);create();
        mvc.perform(post("/api/incidents/42/auto-assign").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.incident.status").value("ASSIGNED"));
    }
}
