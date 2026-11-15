package edu.university.itsm.catalog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface ITServiceRepository extends JpaRepository<ITService,UUID> {
    java.util.Optional<ITService> findByNameIgnoreCase(String name);
}
