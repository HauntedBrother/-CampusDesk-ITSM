package edu.university.itsm.incident.automation;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AssignmentStrategyTest {
    AssignmentStrategy skill=new SkillBasedAssignment(),load=new LoadBasedAssignment();
    AssignmentStrategy.Candidate candidate(int id,int proficiency,long workload,int capacity,boolean available){return new AssignmentStrategy.Candidate(new UUID(0,id),proficiency,workload,capacity,available);}
    @Test void skillRanksProficiencyBeforeWorkload(){var expert=candidate(1,3,4,5,true); var novice=candidate(2,1,0,5,true); assertEquals(expert,skill.select(List.of(novice,expert)).orElseThrow());}
    @Test void skillBreaksProficiencyTiesByWorkload(){var busy=candidate(1,3,3,5,true);var free=candidate(2,3,1,5,true);assertEquals(free,skill.select(List.of(busy,free)).orElseThrow());}
    @Test void loadRanksLowestWorkloadAmongQualified(){var expert=candidate(1,3,4,5,true);var novice=candidate(2,1,0,5,true);assertEquals(novice,load.select(List.of(expert,novice)).orElseThrow());}
    @Test void bothExcludeUnqualifiedUnavailableAndFullTechnicians(){var invalid=List.of(candidate(1,0,0,5,true),candidate(2,3,0,5,false),candidate(3,3,5,5,true),candidate(4,3,7,5,true));assertTrue(skill.select(invalid).isEmpty());assertTrue(load.select(invalid).isEmpty());}
    @Test void emptyPoolHasNoInventedCandidate(){assertTrue(skill.select(List.of()).isEmpty());assertTrue(load.select(List.of()).isEmpty());}
    @Test void tiesAreDeterministicAcrossInputOrders(){var one=candidate(1,2,1,5,true);var two=candidate(2,2,1,5,true);for(var strategy:List.of(skill,load)){assertEquals(one,strategy.select(List.of(two,one)).orElseThrow());assertEquals(one,strategy.select(List.of(one,two)).orElseThrow());}}
    @Test void configurableCapacityIsRespectedAtBoundary(){var slot=candidate(1,3,1,2,true);var full=candidate(2,3,2,2,true);assertEquals(slot,load.select(List.of(full,slot)).orElseThrow());}
}
