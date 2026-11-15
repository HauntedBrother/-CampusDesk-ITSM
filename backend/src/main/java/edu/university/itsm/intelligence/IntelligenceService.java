package edu.university.itsm.intelligence;
import edu.university.itsm.catalog.*;
import edu.university.itsm.identity.*;
import edu.university.itsm.incident.*;
import edu.university.itsm.shared.CurrentUser;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.constraints.*;
import java.util.*;
@Service @Transactional(readOnly=true)
public class IntelligenceService {
    public record Input(@NotBlank @Size(max=200) String title,@NotBlank @Size(max=10000) String description,@NotNull UUID serviceId,Long excludeIncidentId) {}
    public record Duplicate(Long id,String reference,String title,IncidentStatus status,double similarity) {}
    public record Suggestions(List<Duplicate> duplicates,List<RuleCategorizer.Suggestion> categories,String method) {}
    public record RuleInput(@NotNull UUID categoryId,@NotBlank @Size(min=2,max=80) String keyword,@Min(1) @Max(100) int weight,boolean active) {}
    public record RuleView(UUID id,UUID categoryId,String categoryName,String keyword,int weight,boolean active) {}
    private final IncidentRepository incidents;
    private final ITServiceRepository services;
    private final IncidentCategoryRepository categories;
    private final CategoryRuleRepository rules;
    private final RuleCategorizer categorizer;
    private final IncidentPolicy policy;
    private final edu.university.itsm.incident.automation.ConfigurationAuditRepository audit;
    private final edu.university.itsm.incident.automation.AutomationLock lock;
    public IntelligenceService(IncidentRepository incidents,ITServiceRepository services,IncidentCategoryRepository categories,CategoryRuleRepository rules,RuleCategorizer categorizer,IncidentPolicy policy,edu.university.itsm.incident.automation.ConfigurationAuditRepository audit,edu.university.itsm.incident.automation.AutomationLock lock){this.incidents=incidents;this.services=services;this.categories=categories;this.rules=rules;this.categorizer=categorizer;this.policy=policy;this.audit=audit;this.lock=lock;}
    public Suggestions suggest(User actor,Input input) {
        if(!services.existsById(input.serviceId()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Service not found");
        if(input.excludeIncidentId()!=null)policy.requireRead(actor,incidents.findById(input.excludeIncidentId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Incident not found")));
        var search=new IncidentDtos.Search(null,null,null,input.serviceId(),null,null,null,null);
        Specification<Incident> scope=IncidentSpecifications.visibleTo(actor,search).and((r,q,c) -> r.get("status").in(IncidentAutomation.OPEN));
        if(input.excludeIncidentId()!=null)scope=scope.and((r,q,c) -> c.notEqual(r.get("id"),input.excludeIncidentId()));
        List<Duplicate> duplicates=incidents.findAll(scope,PageRequest.of(0,200,Sort.by(Sort.Direction.DESC,"createdAt"))).stream()
            .map(i -> new Duplicate(i.getId(),i.getReference(),i.getTitle(),i.getStatus(),0.8*TextSimilarity.score(input.description(),i.getDescription())+0.2*TextSimilarity.score(input.title(),i.getTitle())))
            .filter(d -> d.similarity()>=0.35).sorted(Comparator.comparingDouble(Duplicate::similarity).reversed().thenComparing(Duplicate::id)).limit(5).toList();
        return new Suggestions(duplicates,categories(input.title(),input.description()),"Normalized trigram Dice similarity; newest 200 accessible active incidents in the selected service; suggestions only");
    }
    public List<RuleCategorizer.Suggestion> categories(String title,String description){return categorizer.suggest(title,description,rules.findByActiveTrue());}
    public UUID resolveCategory(UUID selected,String title,String description) {
        if(selected!=null)return selected;
        return categories(title,description).stream().findFirst().map(RuleCategorizer.Suggestion::categoryId).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,"No category rule matched; select a category"));
    }
    public List<RuleView> rules(User actor){CurrentUser.requireRole(actor,Role.ADMIN);return rules.findAll(Sort.by("keyword")).stream().map(this::view).toList();}
    @Transactional public RuleView save(User actor,RuleInput input) {
        CurrentUser.requireRole(actor,Role.ADMIN);
        lock.acquire();
        IncidentCategory category=categories.findById(input.categoryId()).filter(IncidentCategory::isActive).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,"Select an active category"));
        String keyword=TextSimilarity.normalize(input.keyword());if(keyword.length()<2 || keyword.length()>80)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Normalized keyword must contain 2 to 80 characters");
        var existing=rules.findByCategoryIdAndKeyword(category.getId(),keyword);
        if(existing.isPresent() && existing.get().getWeight()==input.weight() && existing.get().isActive()==input.active())return view(existing.get());
        CategoryRule rule=existing.orElseGet(() -> new CategoryRule(category,keyword,input.weight(),input.active()));
        String before=rule.getId()+" weight="+rule.getWeight()+", active="+rule.isActive();rule.configure(input.weight(),input.active());rules.saveAndFlush(rule);
        audit.save(new edu.university.itsm.incident.automation.ConfigurationAudit(actor,"CATEGORY_RULE",before+" -> "+view(rule)));
        return view(rule);
    }
    private RuleView view(CategoryRule rule){return new RuleView(rule.getId(),rule.getCategory().getId(),rule.getCategory().getName(),rule.getKeyword(),rule.getWeight(),rule.isActive());}
}
