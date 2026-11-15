package edu.university.itsm.incident.automation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ConfigurationAuditRepository extends JpaRepository<ConfigurationAudit,Long> {
    List<ConfigurationAudit> findTop100ByOrderByIdDesc();
}
