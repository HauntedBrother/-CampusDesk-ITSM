package edu.university.itsm.shared;
import edu.university.itsm.identity.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Component
public class CurrentUser {
    private final UserRepository users;
    public CurrentUser(UserRepository users) { this.users=users; }
    public User get(Jwt jwt) {
        return users.findById(UUID.fromString(jwt.getSubject())).filter(User::isEnabled)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Account is inactive"));
    }
    public static void requireRole(User user, Role role) {
        if (user.getRole()!=role) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This operation is not permitted for your role");
    }
}
