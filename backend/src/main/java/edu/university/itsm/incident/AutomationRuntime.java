package edu.university.itsm.incident;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;
@Configuration @EnableScheduling
public class AutomationRuntime {
    @Bean public Clock automationClock(){return Clock.systemUTC();}
}
