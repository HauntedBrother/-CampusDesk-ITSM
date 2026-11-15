package edu.university.itsm.intelligence;
import edu.university.itsm.catalog.IncidentCategory;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class IntelligenceTest {
    @Test void identicalNormalizedDescriptionsMatch(){assertEquals(1,TextSimilarity.score("Library WI-FI unavailable!","library wi fi unavailable"));}
    @Test void unicodeNormalizationIsStable(){assertEquals(1,TextSimilarity.score("Ｎｅｔｗｏｒｋ failure","Network failure"));}
    @Test void missingOrShortDescriptionsDoNotMatch(){assertEquals(0,TextSimilarity.score(null,"network"));assertEquals(0,TextSimilarity.score("a","a"));}
    @Test void unrelatedDescriptionsStayBelowThreshold(){assertTrue(TextSimilarity.score("Cannot connect to campus wireless network","Payroll software installation failure")<.35);}
    @Test void partialMatchesAreSymmetric(){double score=TextSimilarity.score("Campus wireless network is unavailable","Campus wireless network keeps disconnecting");assertTrue(score>.35 && score<1);assertEquals(score,TextSimilarity.score("Campus wireless network keeps disconnecting","Campus wireless network is unavailable"));}
    @Test void categorizationUsesWholePhrasesAndWeights(){var network=new IncidentCategory("Network","");var software=new IncidentCategory("Software","");var matches=new RuleCategorizer().suggest("Network application failure","Cannot sign in",List.of(new CategoryRule(network,"network",5,true),new CategoryRule(network,"sign in",8,true),new CategoryRule(software,"application",10,true)));assertEquals(network.getId(),matches.getFirst().categoryId());assertEquals(13,matches.getFirst().score());assertEquals(2,matches.size());}
    @Test void rulesDoNotMatchInsideWords(){assertFalse(TextSimilarity.containsPhrase("Install a catalog application","log"));assertTrue(new RuleCategorizer().suggest("Catalog","Nothing matches",List.of(new CategoryRule(new IncidentCategory("Authentication",""),"log",5,true))).isEmpty());}
    @Test void disabledRulesAndCategoriesAreExcluded(){var category=new IncidentCategory("Network","");var disabled=new CategoryRule(category,"wifi",5,false);assertTrue(new RuleCategorizer().suggest("wifi","wifi",List.of(disabled)).isEmpty());category.update("Network","",false);assertTrue(new RuleCategorizer().suggest("wifi","wifi",List.of(new CategoryRule(category,"wifi",5,true))).isEmpty());}
    @Test void repeatedKeywordDoesNotInflateScore(){var rule=new CategoryRule(new IncidentCategory("Network",""),"wifi",5,true);assertEquals(5,new RuleCategorizer().suggest("wifi wifi","wifi wifi",List.of(rule)).getFirst().score());}
    @Test void canonicalKeywordLengthIsValidatedBeforePersistence(){
        var categories=mock(edu.university.itsm.catalog.IncidentCategoryRepository.class);var rules=mock(CategoryRuleRepository.class);var category=new IncidentCategory("Network","");when(categories.findById(category.getId())).thenReturn(Optional.of(category));
        var service=new IntelligenceService(null,null,categories,rules,null,null,null,mock(edu.university.itsm.incident.automation.AutomationLock.class));
        var admin=new edu.university.itsm.identity.User("Admin","admin@uni.edu","hash",edu.university.itsm.identity.Role.ADMIN);
        for(String keyword:List.of("!!!","\uFB03".repeat(40))){var error=assertThrows(org.springframework.web.server.ResponseStatusException.class,() -> service.save(admin,new IntelligenceService.RuleInput(category.getId(),keyword,5,true)));assertEquals(400,error.getStatusCode().value());}
        verifyNoInteractions(rules);
    }
}
