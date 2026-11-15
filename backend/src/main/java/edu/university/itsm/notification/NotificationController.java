package edu.university.itsm.notification;
import edu.university.itsm.shared.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/notifications")
public class NotificationController {
    public record ReadInput(@NotNull Boolean read) {}
    private final NotificationService notifications;
    private final CurrentUser current;
    public NotificationController(NotificationService notifications,CurrentUser current){this.notifications=notifications;this.current=current;}
    @GetMapping public NotificationService.Notifications list(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="false") boolean unreadOnly,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return notifications.list(current.get(jwt),unreadOnly,page,size);}
    @PostMapping("/{id}/read") public NotificationService.View read(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody ReadInput input){return notifications.read(current.get(jwt),id,input.read());}
    @GetMapping("/email-deliveries") public org.springframework.data.domain.Page<NotificationService.EmailView> emails(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return notifications.emailLogs(current.get(jwt),page,size);}
}
