package edu.university.itsm.dashboard;
import org.springframework.web.bind.annotation.*;
import edu.university.itsm.identity.Role;
@RestController @RequestMapping("/api")
public class DashboardController {
    public record Dashboard(Role role, String phase) {}
    @GetMapping("/health") public java.util.Map<String,String> health() { return java.util.Map.of("status","UP"); }
    @GetMapping("/dashboards/employee") public Dashboard employee() { return new Dashboard(Role.EMPLOYEE,"FOUNDATION"); }
    @GetMapping("/dashboards/technician") public Dashboard technician() { return new Dashboard(Role.TECHNICIAN,"FOUNDATION"); }
    @GetMapping("/dashboards/admin") public Dashboard admin() { return new Dashboard(Role.ADMIN,"FOUNDATION"); }
}
