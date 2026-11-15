package edu.university.itsm.catalog;

import edu.university.itsm.identity.*;
import edu.university.itsm.shared.CurrentUser;
import edu.university.itsm.incident.IncidentAutomation;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @Transactional(readOnly=true)
public class CatalogService {
    private final ITServiceRepository services;
    private final IncidentCategoryRepository categories;
    private final UserRepository users;
    private final TechnicianProfileRepository profiles;
    private final IncidentAutomation automation;
    public CatalogService(ITServiceRepository services, IncidentCategoryRepository categories, UserRepository users, TechnicianProfileRepository profiles,IncidentAutomation automation) {
        this.services=services; this.categories=categories; this.users=users; this.profiles=profiles;
        this.automation=automation;
    }
    public List<CatalogDtos.ServiceView> services(User actor, boolean all) {
        if (all) CurrentUser.requireRole(actor,Role.ADMIN);
        return services.findAll(Sort.by("name")).stream().filter(s -> all || s.isActive()).map(CatalogDtos.ServiceView::of).toList();
    }
    public List<CatalogDtos.CategoryView> categories(User actor, boolean all) {
        if (all) CurrentUser.requireRole(actor,Role.ADMIN);
        return categories.findAll(Sort.by("name")).stream().filter(c -> all || c.isActive()).map(CatalogDtos.CategoryView::of).toList();
    }
    @Transactional public CatalogDtos.ServiceView saveService(User actor, UUID id, CatalogDtos.ServiceInput input) {
        CurrentUser.requireRole(actor,Role.ADMIN);
        automation.lock();
        ITService service=id==null ? new ITService(input.name(),input.description(),input.businessCriticality()) : services.findById(id).orElseThrow(CatalogService::missing);
        int previous=service.getBusinessCriticality();
        service.update(input.name(),input.description(),input.businessCriticality(),input.active());
        services.saveAndFlush(service);
        if(previous!=service.getBusinessCriticality()) automation.refreshService(service.getId(),actor);
        return CatalogDtos.ServiceView.of(service);
    }
    @Transactional public CatalogDtos.CategoryView saveCategory(User actor, UUID id, CatalogDtos.CategoryInput input) {
        CurrentUser.requireRole(actor,Role.ADMIN);
        automation.lock();
        IncidentCategory category=id==null ? new IncidentCategory(input.name(),input.description()) : categories.findById(id).orElseThrow(CatalogService::missing);
        category.update(input.name(),input.description(),input.active());
        return CatalogDtos.CategoryView.of(categories.saveAndFlush(category));
    }
    public List<CatalogDtos.TechnicianView> technicians(User actor) {
        CurrentUser.requireRole(actor,Role.ADMIN);
        return users.findByRoleAndEnabledTrueOrderByDisplayNameAsc(Role.TECHNICIAN).stream().flatMap(user -> profiles.findById(user.getId()).stream()
            .map(profile -> new CatalogDtos.TechnicianView(user.getId(),user.getDisplayName(),profile.getCapacity(),profile.isAvailable()))).toList();
    }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Catalog entry not found"); }
}
