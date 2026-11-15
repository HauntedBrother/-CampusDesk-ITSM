package edu.university.itsm.intelligence;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class RuleCategorizer {
    public record Suggestion(UUID categoryId,String categoryName,int score,List<String> matchedKeywords) {}
    public List<Suggestion> suggest(String title,String description,List<CategoryRule> rules) {
        String text=title+" "+description;
        Map<UUID,List<CategoryRule>> matches=new HashMap<>();
        for(CategoryRule rule:rules)if(rule.isActive() && rule.getCategory().isActive() && TextSimilarity.containsPhrase(text,rule.getKeyword()))matches.computeIfAbsent(rule.getCategory().getId(),id -> new ArrayList<>()).add(rule);
        return matches.entrySet().stream().map(e -> new Suggestion(e.getKey(),e.getValue().getFirst().getCategory().getName(),e.getValue().stream().mapToInt(CategoryRule::getWeight).sum(),e.getValue().stream().map(CategoryRule::getKeyword).sorted().toList()))
            .sorted(Comparator.comparingInt(Suggestion::score).reversed().thenComparing(Suggestion::categoryName).thenComparing(s -> s.categoryId().toString())).limit(5).toList();
    }
}
