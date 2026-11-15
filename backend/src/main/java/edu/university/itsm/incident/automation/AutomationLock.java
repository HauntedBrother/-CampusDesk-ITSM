package edu.university.itsm.incident.automation;
import edu.university.itsm.incident.InvalidIncidentOperation;
import org.springframework.stereotype.Component;
@Component
public class AutomationLock {
    private final AutomationSettingsRepository settings;
    public AutomationLock(AutomationSettingsRepository settings){this.settings=settings;}
    /** All automation/configuration writers take this database lock before incident/profile locks. */
    public AutomationSettings acquire(){return settings.lockSettings().orElseThrow(() -> new InvalidIncidentOperation("Automation settings are missing; run database migrations"));}
}
