package edu.university.itsm.identity;
import edu.university.itsm.incident.*;
import edu.university.itsm.incident.automation.AssignmentEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"app.jwt-secret=development-seed-test-secret-at-least-32-bytes","spring.profiles.active=dev","DEV_SEED_PASSWORD=DevelopmentOnlyPassword123!","debug=false","app.sla.monitor-enabled=false","app.notifications.initial-delay-ms=3600000"})
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class DevelopmentScenarioIntegrationTest {
    @Container static PostgreSQLContainer<?> db=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",db::getJdbcUrl);r.add("spring.datasource.username",db::getUsername);r.add("spring.datasource.password",db::getPassword);}
    @Autowired DevelopmentScenarioSeed seed;
    @Autowired UserRepository users;
    @Autowired TechnicianProfileRepository profiles;
    @Autowired IncidentRepository incidents;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Test void developmentSeedProvidesVariedRealWorkloadsAndDoesNotReplay(){
        tx.executeWithoutResult(t -> {
            var network=users.findByEmail("network.tech@university.test").orElseThrow();var software=users.findByEmail("software.tech@university.test").orElseThrow();var standby=users.findByEmail("standby.tech@university.test").orElseThrow();
            assertEquals(2,incidents.countByTechnicianIdAndStatusIn(network.getId(),AssignmentEngine.ACTIVE));assertEquals(1,incidents.countByTechnicianIdAndStatusIn(software.getId(),AssignmentEngine.ACTIVE));assertEquals(0,incidents.countByTechnicianIdAndStatusIn(standby.getId(),AssignmentEngine.ACTIVE));
            var expert=profiles.findById(network.getId()).orElseThrow();var reserve=profiles.findById(standby.getId()).orElseThrow();assertTrue(expert.isAvailable());assertFalse(reserve.isAvailable());assertEquals(5,expert.getCapacity());assertFalse(expert.getSkills().isEmpty());
        });
        var tables=List.of("users","incidents","incident_history","notification_outbox","in_app_notifications","simulated_email_deliveries","category_rules");var before=tables.stream().map(name -> jdbc.queryForObject("select count(*) from "+name,Long.class)).toList();
        seed.run(new DefaultApplicationArguments());assertEquals(before,tables.stream().map(name -> jdbc.queryForObject("select count(*) from "+name,Long.class)).toList());assertEquals(1,jdbc.queryForObject("select count(*) from development_seed_versions",Long.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from notification_outbox where processed_at is null",Long.class));assertTrue(jdbc.queryForObject("select count(*) from simulated_email_deliveries",Long.class)>0);
    }
}
