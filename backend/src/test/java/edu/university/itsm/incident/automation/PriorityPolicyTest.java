package edu.university.itsm.incident.automation;
import edu.university.itsm.incident.IncidentPriority;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
class PriorityPolicyTest {
    MultiplicativePriorityPolicy policy=new MultiplicativePriorityPolicy();
    static Stream<Arguments> factors(){return IntStream.rangeClosed(1,3).boxed().flatMap(i -> IntStream.rangeClosed(1,3).boxed().flatMap(u -> IntStream.rangeClosed(1,3).mapToObj(c -> Arguments.of(i,u,c))));}
    @ParameterizedTest @MethodSource("factors") void everyFactorCombination(int i,int u,int c) {
        int score=i*u*c; assertEquals(score,policy.score(i,u,c));
        assertEquals(score>=18 ? IncidentPriority.P1 : score>=9 ? IncidentPriority.P2 : score>=4 ? IncidentPriority.P3 : IncidentPriority.P4,policy.calculate(i,u,c));
    }
    @ParameterizedTest @CsvSource({"1,P4","3,P4","4,P3","8,P3","9,P2","17,P2","18,P1","27,P1"})
    void exactThresholds(int score,IncidentPriority expected){assertEquals(expected,policy.fromScore(score));}
    @Test void rejectsInvalidFactorsAndScores(){assertThrows(IllegalArgumentException.class,() -> policy.calculate(0,1,1)); assertThrows(IllegalArgumentException.class,() -> policy.calculate(1,4,1)); assertThrows(IllegalArgumentException.class,() -> policy.calculate(1,1,-1)); assertThrows(IllegalArgumentException.class,() -> policy.fromScore(28));}
}
