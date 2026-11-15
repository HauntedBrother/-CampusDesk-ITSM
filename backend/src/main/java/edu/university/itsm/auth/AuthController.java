package edu.university.itsm.auth;
import edu.university.itsm.identity.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final UserRepository users;
    public AuthController(AuthService auth, UserRepository users) { this.auth=auth; this.users=users; }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.Token register(@Valid @RequestBody AuthDtos.Register request) { return auth.register(request); }
    @PostMapping("/login")
    public AuthDtos.Token login(@Valid @RequestBody AuthDtos.Login request) { return auth.login(request); }
    @GetMapping("/me")
    public AuthDtos.Profile me(@AuthenticationPrincipal Jwt jwt) { return AuthDtos.Profile.of(users.findById(UUID.fromString(jwt.getSubject())).orElseThrow()); }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal Jwt jwt) { auth.logout(jwt); }
}
