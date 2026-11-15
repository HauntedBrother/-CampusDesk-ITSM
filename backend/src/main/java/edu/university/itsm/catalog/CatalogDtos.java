package edu.university.itsm.catalog;
import jakarta.validation.constraints.*;
import java.util.UUID;

public final class CatalogDtos {
    private CatalogDtos() {}
    public record ServiceInput(@NotBlank @Size(max=100) String name, @NotNull @Size(max=1000) String description,
                               @Min(1) @Max(3) int businessCriticality, boolean active) {}
    public record CategoryInput(@NotBlank @Size(max=100) String name, @NotNull @Size(max=1000) String description, boolean active) {}
    public record ServiceView(UUID id,String name,String description,int businessCriticality,boolean active) {
        public static ServiceView of(ITService service) { return new ServiceView(service.getId(),service.getName(),service.getDescription(),service.getBusinessCriticality(),service.isActive()); }
    }
    public record CategoryView(UUID id,String name,String description,boolean active) {
        public static CategoryView of(IncidentCategory category) { return new CategoryView(category.getId(),category.getName(),category.getDescription(),category.isActive()); }
    }
    public record TechnicianView(UUID id,String displayName,int capacity,boolean available) {}
}
