package edu.university.itsm.catalog;

import edu.university.itsm.shared.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/catalog")
public class CatalogController {
    private final CatalogService catalog;
    private final CurrentUser current;
    public CatalogController(CatalogService catalog, CurrentUser current) { this.catalog=catalog; this.current=current; }
    @GetMapping("/services") public List<CatalogDtos.ServiceView> services(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="false") boolean includeInactive) { return catalog.services(current.get(jwt),includeInactive); }
    @GetMapping("/categories") public List<CatalogDtos.CategoryView> categories(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="false") boolean includeInactive) { return catalog.categories(current.get(jwt),includeInactive); }
    @GetMapping("/technicians") public List<CatalogDtos.TechnicianView> technicians(@AuthenticationPrincipal Jwt jwt) { return catalog.technicians(current.get(jwt)); }
    @PostMapping("/services") @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.ServiceView createService(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CatalogDtos.ServiceInput input) { return catalog.saveService(current.get(jwt),null,input); }
    @PutMapping("/services/{id}") public CatalogDtos.ServiceView updateService(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody CatalogDtos.ServiceInput input) { return catalog.saveService(current.get(jwt),id,input); }
    @PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.CategoryView createCategory(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CatalogDtos.CategoryInput input) { return catalog.saveCategory(current.get(jwt),null,input); }
    @PutMapping("/categories/{id}") public CatalogDtos.CategoryView updateCategory(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody CatalogDtos.CategoryInput input) { return catalog.saveCategory(current.get(jwt),id,input); }
}
