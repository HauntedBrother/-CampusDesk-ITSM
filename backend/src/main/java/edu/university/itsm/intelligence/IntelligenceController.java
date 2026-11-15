package edu.university.itsm.intelligence;
import edu.university.itsm.shared.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/intelligence")
public class IntelligenceController {
    private final IntelligenceService intelligence;
    private final CurrentUser current;
    public IntelligenceController(IntelligenceService intelligence,CurrentUser current){this.intelligence=intelligence;this.current=current;}
    @PostMapping("/suggestions") public IntelligenceService.Suggestions suggest(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody IntelligenceService.Input input){return intelligence.suggest(current.get(jwt),input);}
    @GetMapping("/rules") public List<IntelligenceService.RuleView> rules(@AuthenticationPrincipal Jwt jwt){return intelligence.rules(current.get(jwt));}
    @PutMapping("/rules") public IntelligenceService.RuleView save(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody IntelligenceService.RuleInput input){return intelligence.save(current.get(jwt),input);}
}
