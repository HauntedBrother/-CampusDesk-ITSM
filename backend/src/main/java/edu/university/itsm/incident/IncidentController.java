package edu.university.itsm.incident;

import edu.university.itsm.shared.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/incidents")
public class IncidentController {
    private final IncidentService incidents;
    private final CurrentUser current;
    public IncidentController(IncidentService incidents,CurrentUser current) { this.incidents=incidents; this.current=current; }
    @GetMapping public IncidentDtos.Page list(@AuthenticationPrincipal Jwt jwt,@Valid @ModelAttribute IncidentDtos.Search search,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="createdAt") String sort,@RequestParam(defaultValue="desc") String direction) { return incidents.search(current.get(jwt),search,page,size,sort,direction); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public IncidentDtos.Detail create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody IncidentDtos.Input input) { return incidents.create(current.get(jwt),input); }
    @GetMapping("/{id}") public IncidentDtos.Detail get(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) { return incidents.get(current.get(jwt),id); }
    @PutMapping("/{id}") public IncidentDtos.Detail update(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@Valid @RequestBody IncidentDtos.Input input) { return incidents.update(current.get(jwt),id,input); }
    @PostMapping("/{id}/classify") public IncidentDtos.Detail classify(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) { return incidents.classify(current.get(jwt),id); }
    @PostMapping("/{id}/assign") public IncidentDtos.Detail assign(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@Valid @RequestBody IncidentDtos.Assignment input) { return incidents.assign(current.get(jwt),id,input); }
    @PostMapping("/{id}/auto-assign") public IncidentDtos.Detail autoAssign(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) { return incidents.autoAssign(current.get(jwt),id); }
    @PostMapping("/{id}/acknowledge") public IncidentDtos.Detail acknowledge(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) { return incidents.acknowledge(current.get(jwt),id); }
    @PostMapping("/{id}/start") public IncidentDtos.Detail start(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) { return incidents.start(current.get(jwt),id); }
    @PostMapping("/{id}/resolve") public IncidentDtos.Detail resolve(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@Valid @RequestBody IncidentDtos.Resolution input) { return incidents.resolve(current.get(jwt),id,input); }
    @PostMapping("/{id}/verify") public IncidentDtos.Detail verify(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) { return incidents.verify(current.get(jwt),id); }
    @PostMapping("/{id}/close") public IncidentDtos.Detail close(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) { return incidents.close(current.get(jwt),id); }
    @PostMapping("/{id}/comments") public IncidentDtos.Detail comment(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@Valid @RequestBody IncidentDtos.Comment input) { return incidents.comment(current.get(jwt),id,input); }
    @PostMapping("/{id}/feedback") public IncidentDtos.Detail feedback(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@Valid @RequestBody IncidentDtos.Feedback input) { return incidents.feedback(current.get(jwt),id,input); }
    @PostMapping("/{id}/override") public IncidentDtos.Detail override(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@Valid @RequestBody IncidentDtos.Override input) { return incidents.override(current.get(jwt),id,input); }
}
