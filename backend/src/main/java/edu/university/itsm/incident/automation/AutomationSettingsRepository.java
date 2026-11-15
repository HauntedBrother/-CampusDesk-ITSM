package edu.university.itsm.incident.automation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
public interface AutomationSettingsRepository extends JpaRepository<AutomationSettings,Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from AutomationSettings s where s.id=1")
    Optional<AutomationSettings> lockSettings();
}
