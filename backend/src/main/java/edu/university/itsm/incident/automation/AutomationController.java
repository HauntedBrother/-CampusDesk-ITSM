package edu.university.itsm.incident.automation;
import edu.university.itsm.shared.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/admin/automation")
public class AutomationController {
    private final AutomationConfigurationService configuration;
    private final CurrentUser current;
    public AutomationController(AutomationConfigurationService configuration,CurrentUser current){this.configuration=configuration; this.current=current;}
    @GetMapping("/settings") public AutomationDtos.Settings settings(@AuthenticationPrincipal Jwt jwt){return configuration.settings(current.get(jwt));}
    @PutMapping("/settings") public AutomationDtos.Settings configure(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody AutomationDtos.Settings input){return configuration.configure(current.get(jwt),input);}
    @GetMapping("/policies") public List<AutomationDtos.PolicyView> policies(@AuthenticationPrincipal Jwt jwt){return configuration.policies(current.get(jwt));}
    @PutMapping("/policies") public AutomationDtos.PolicyView policy(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody AutomationDtos.PolicyInput input){return configuration.savePolicy(current.get(jwt),input);}
    @GetMapping("/technicians") public List<AutomationDtos.TechnicianView> technicians(@AuthenticationPrincipal Jwt jwt){return configuration.technicians(current.get(jwt));}
    @PutMapping("/technicians/{id}") public AutomationDtos.TechnicianView technician(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody AutomationDtos.TechnicianInput input){return configuration.configureTechnician(current.get(jwt),id,input);}
    @GetMapping("/history") public List<AutomationDtos.AuditView> history(@AuthenticationPrincipal Jwt jwt){return configuration.history(current.get(jwt));}
}
