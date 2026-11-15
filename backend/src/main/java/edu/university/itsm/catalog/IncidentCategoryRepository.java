package edu.university.itsm.catalog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface IncidentCategoryRepository extends JpaRepository<IncidentCategory,UUID> {
    java.util.Optional<IncidentCategory> findByNameIgnoreCase(String name);
}
