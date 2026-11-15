package edu.university.itsm.incident;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
public final class IncidentSorting {
    private IncidentSorting() {}
    private static final Map<String,String> FIELDS=Map.ofEntries(Map.entry("id","id"),Map.entry("title","title"),Map.entry("status","status"),Map.entry("priority","priority"),Map.entry("createdAt","createdAt"),Map.entry("updatedAt","updatedAt"),Map.entry("resolvedAt","resolvedAt"),Map.entry("service","service.name"),Map.entry("category","category.name"),Map.entry("technician","technician.displayName"),Map.entry("responseDueAt","sla.responseDueAt"),Map.entry("resolutionDueAt","sla.resolutionDueAt"));
    public static Sort sort(String field,String direction){
        if(!FIELDS.containsKey(field) || !("asc".equalsIgnoreCase(direction)||"desc".equalsIgnoreCase(direction)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid sort field or direction");
        Sort.Direction order=Sort.Direction.fromString(direction);
        return Sort.by(new Sort.Order(order,FIELDS.get(field)),new Sort.Order(order,"id"));
    }
}
