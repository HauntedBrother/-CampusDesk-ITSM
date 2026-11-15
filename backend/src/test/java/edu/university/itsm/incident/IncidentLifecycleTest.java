package edu.university.itsm.incident;

import edu.university.itsm.catalog.*;
import edu.university.itsm.identity.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static edu.university.itsm.incident.IncidentStatus.*;

class IncidentLifecycleTest {
    static Stream<Arguments> transitions() {
        return Arrays.stream(IncidentStatus.values()).flatMap(from -> Arrays.stream(IncidentStatus.values()).map(to -> Arguments.of(from,to)));
    }
    @ParameterizedTest @MethodSource("transitions")
    void stateObjectsAllowOnlyTheNextStep(IncidentStatus from,IncidentStatus to) {
        if(to.ordinal()==from.ordinal()+1) assertDoesNotThrow(() -> IncidentStates.of(from).requireTransition(to));
        else assertThrows(InvalidIncidentOperation.class,() -> IncidentStates.of(from).requireTransition(to));
    }
    User employee=new User("Employee","e@uni.edu","hash",Role.EMPLOYEE);
    User technician=new User("Technician","t@uni.edu","hash",Role.TECHNICIAN);
    Incident incident() { return new Incident(employee,"Wi-Fi unavailable","Cannot connect",new ITService("Wi-Fi","Campus network",3),new IncidentCategory("Network","Connectivity"),3,2); }
    void investigate(Incident i) { i.transition(CLASSIFIED); i.assign(technician); i.transition(ACKNOWLEDGED); i.transition(IN_PROGRESS); }
    @Test void completeLifecycleRecordsMilestonesWithoutInventingPriority() {
        Incident i=incident(); assertNull(i.getPriority()); assertEquals(NEW,i.getStatus());
        investigate(i); i.resolve("Replaced the failed access point"); i.transition(VERIFIED); i.transition(CLOSED);
        assertNotNull(i.getAcknowledgedAt()); assertNotNull(i.getResolvedAt()); assertNotNull(i.getVerifiedAt()); assertNotNull(i.getClosedAt());
        assertEquals(CLOSED,i.getStatus()); assertNull(i.getPriority());
    }
    @Test void cannotSkipToClosure() { assertThrows(InvalidIncidentOperation.class,() -> incident().transition(CLOSED)); }
    @Test void assignmentRequiresClassification() { assertThrows(InvalidIncidentOperation.class,() -> incident().assign(technician)); }
    @Test void resolutionRequiresInvestigation() { assertThrows(InvalidIncidentOperation.class,() -> incident().resolve("A fix")); }
    @Test void blankResolutionRejected() { Incident i=incident(); investigate(i); assertThrows(InvalidIncidentOperation.class,() -> i.resolve("   ")); }
    @Test void verificationMustPrecedeNormalClosure() { Incident i=incident(); investigate(i); i.resolve("Fixed"); assertThrows(InvalidIncidentOperation.class,() -> i.transition(CLOSED)); }
    @Test void feedbackRequiresResolutionAndValidRating() {
        Incident i=incident(); assertThrows(InvalidIncidentOperation.class,() -> i.provideFeedback(5,"Good"));
        investigate(i); i.resolve("Fixed"); assertThrows(InvalidIncidentOperation.class,() -> i.provideFeedback(6,"Good"));
        i.provideFeedback(4,"Working again"); assertEquals(4,i.getFeedbackRating());
    }
    @Test void overrideCannotInventAssignmentOrResolution() {
        Incident i=incident(); assertThrows(InvalidIncidentOperation.class,() -> i.overrideTo(IN_PROGRESS));
        investigate(i); assertThrows(InvalidIncidentOperation.class,() -> i.overrideTo(CLOSED));
    }
    @Test void reopeningClearsMilestonesAndFeedback() {
        Incident i=incident(); investigate(i); i.resolve("Fixed"); i.provideFeedback(5,"Good"); i.transition(VERIFIED); i.transition(CLOSED);
        i.overrideTo(CLASSIFIED); assertNull(i.getTechnician()); assertNull(i.getAcknowledgedAt()); assertNull(i.getResolution());
        assertNull(i.getResolvedAt()); assertNull(i.getVerifiedAt()); assertNull(i.getClosedAt()); assertNull(i.getFeedback()); assertNull(i.getFeedbackRating());
    }
    @Test void inputScoresAreBounded() { assertThrows(InvalidIncidentOperation.class,() -> new Incident(employee,"Title","Description",new ITService("S","",1),new IncidentCategory("C",""),0,4)); }
}
