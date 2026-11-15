package edu.university.itsm.incident;

import edu.university.itsm.catalog.*;
import edu.university.itsm.identity.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static edu.university.itsm.incident.IncidentStatus.*;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {
    @Mock IncidentRepository incidents;
    @Mock IncidentCommentRepository comments;
    @Mock IncidentHistoryRepository history;
    @Mock ITServiceRepository services;
    @Mock IncidentCategoryRepository categories;
    @Mock IncidentAutomation automation;
    @Mock edu.university.itsm.intelligence.IntelligenceService intelligence;
    IncidentService application;
    User employee=new User("Employee","e@uni.edu","hash",Role.EMPLOYEE);
    User other=new User("Other","o@uni.edu","hash",Role.EMPLOYEE);
    User tech=new User("Tech","t@uni.edu","hash",Role.TECHNICIAN);
    User admin=new User("Admin","a@uni.edu","hash",Role.ADMIN);
    ITService service=new ITService("Network","",3);
    IncidentCategory category=new IncidentCategory("Network","");
    Incident incident;
    @BeforeEach void setup() {
        application=new IncidentService(incidents,comments,history,services,categories,new IncidentPolicy(),automation,new IncidentAudit(history,mock(edu.university.itsm.notification.NotificationEvents.class)),intelligence);
        incident=new Incident(employee,"Wi-Fi issue","Cannot connect",service,category,2,3); ReflectionTestUtils.setField(incident,"id",1L);
        lenient().when(incidents.lockById(1L)).thenReturn(Optional.of(incident));
        lenient().when(incidents.findById(1L)).thenReturn(Optional.of(incident));
    }
    void assigned() { incident.transition(CLASSIFIED); incident.assign(tech); }
    void resolved() { assigned(); incident.transition(ACKNOWLEDGED); incident.transition(IN_PROGRESS); incident.resolve("Replaced access point"); }
    @Test void hidesAnotherEmployeesIncident() {
        assertEquals(404,assertThrows(ResponseStatusException.class,() -> application.get(other,1L)).getStatusCode().value());
        verifyNoInteractions(comments,history);
    }
    @Test void unassignedTechnicianCannotReadIncident() { assertThrows(ResponseStatusException.class,() -> application.get(tech,1L)); }
    @Test void employeesCannotClassifyOwnIncident() { assertEquals(403,assertThrows(ResponseStatusException.class,() -> application.classify(employee,1L)).getStatusCode().value()); }
    @Test void employeeCannotAcknowledgeAnAssignedIncident() { assigned(); assertThrows(ResponseStatusException.class,() -> application.acknowledge(employee,1L)); }
    @Test void assignedTechnicianAcknowledgesAndInvestigates() { assigned(); application.acknowledge(tech,1L); application.start(tech,1L); assertEquals(IN_PROGRESS,incident.getStatus()); }
    @Test void employeeEditsOnlyBeforeAssignment() { assigned(); assertEquals(409,assertThrows(ResponseStatusException.class,() -> application.update(employee,1L,new IncidentDtos.Input("New title","New body",service.getId(),category.getId(),1,1))).getStatusCode().value()); }
    @Test void employeeCannotCreateInternalNotes() { assertEquals(403,assertThrows(ResponseStatusException.class,() -> application.comment(employee,1L,new IncidentDtos.Comment("Secret",true))).getStatusCode().value()); verifyNoInteractions(comments); }
    @Test void employeeReadsOnlyPublicCommentsAndHistory() { application.get(employee,1L); verify(comments).findByIncidentIdAndInternalFalseOrderByIdAsc(1L); verify(history).findByIncidentIdAndInternalFalseOrderByIdAsc(1L); }
    @Test void internalInvestigationNoteGetsInternalAuditRecord() {
        assigned(); application.comment(tech,1L,new IncidentDtos.Comment("Private diagnostic details",true));
        var audit=ArgumentCaptor.forClass(IncidentHistory.class); verify(history).saveAndFlush(audit.capture()); assertTrue(audit.getValue().isInternal());
    }
    @Test void onlyReporterVerifiesAndNormalClosureRequiresVerification() {
        resolved(); assertThrows(ResponseStatusException.class,() -> application.verify(tech,1L));
        assertThrows(ResponseStatusException.class,() -> application.close(admin,1L));
        application.verify(employee,1L); application.close(employee,1L); assertEquals(CLOSED,incident.getStatus());
    }
    @Test void adminMustUseExplicitOverrideToBypassEmployeeVerification() {
        resolved(); assertThrows(ResponseStatusException.class,() -> application.verify(admin,1L));
        application.override(admin,1L,new IncidentDtos.Override(CLOSED,"Employee unavailable; manager authorized closure"));
        var audit=ArgumentCaptor.forClass(IncidentHistory.class); verify(history).saveAndFlush(audit.capture());
        assertEquals("ADMIN_OVERRIDE",audit.getValue().getAction()); assertTrue(audit.getValue().getDescription().contains("manager authorized")); assertEquals(CLOSED,incident.getStatus());
    }
    @Test void nonAdminCannotOverride() { assertEquals(403,assertThrows(ResponseStatusException.class,() -> application.override(employee,1L,new IncidentDtos.Override(CLASSIFIED,"Bypassing classification"))).getStatusCode().value()); }
    @Test void manualAssignmentRespectsCapacity() {
        incident.transition(CLASSIFIED);
        when(automation.eligible(incident,tech.getId())).thenThrow(new InvalidIncidentOperation("Technician capacity has been reached"));
        assertThrows(InvalidIncidentOperation.class,() -> application.assign(admin,1L,new IncidentDtos.Assignment(tech.getId())));
        assertEquals(CLASSIFIED,incident.getStatus()); verifyNoInteractions(history);
    }
    @Test void activeTechnicianCanBeAssignedManually() {
        incident.transition(CLASSIFIED); when(automation.eligible(incident,tech.getId())).thenReturn(tech);
        application.assign(admin,1L,new IncidentDtos.Assignment(tech.getId())); assertEquals(ASSIGNED,incident.getStatus()); assertSame(tech,incident.getTechnician());
    }
    @Test void creationDelegatesAutomationAndRecordsHistory() {
        when(services.findById(service.getId())).thenReturn(Optional.of(service)); when(categories.findById(category.getId())).thenReturn(Optional.of(category));
        when(incidents.saveAndFlush(any())).thenAnswer(a -> { Incident i=a.getArgument(0); ReflectionTestUtils.setField(i,"id",2L); return i; });
        var detail=application.create(employee,new IncidentDtos.Input("Title","Description",service.getId(),category.getId(),2,3));
        assertEquals(NEW,detail.incident().status()); verify(history).saveAndFlush(any()); verify(automation).refresh(any(),eq(employee),eq(true));
    }
    @Test void adminCannotCreateAnEmployeeReport() { assertThrows(ResponseStatusException.class,() -> application.create(admin,new IncidentDtos.Input("Title","Description",service.getId(),category.getId(),2,3))); }
    @Test void inactiveCatalogCannotBeUsedForNewIncident() {
        service.update("Network","",3,false); when(services.findById(service.getId())).thenReturn(Optional.of(service));
        assertThrows(ResponseStatusException.class,() -> application.create(employee,new IncidentDtos.Input("Title","Description",service.getId(),category.getId(),2,3)));
    }
    @Test void invalidPaginationAndDateRangesAreRejected() {
        var empty=new IncidentDtos.Search(null,null,null,null,null,null,null,null);
        assertThrows(ResponseStatusException.class,() -> application.search(employee,empty,0,101));
        var range=new IncidentDtos.Search(null,null,null,null,null,null,java.time.LocalDate.of(2026,11,20),java.time.LocalDate.of(2026,11,1));
        assertThrows(ResponseStatusException.class,() -> IncidentSpecifications.visibleTo(employee,range));
    }
}
