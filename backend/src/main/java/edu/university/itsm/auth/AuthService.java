package edu.university.itsm.auth;
import edu.university.itsm.identity.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
@Service
public class AuthService {
    private final UserRepository users;
    private final SessionRepository sessions;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final Duration ttl;
    public AuthService(UserRepository users, SessionRepository sessions, PasswordEncoder passwords, JwtEncoder encoder,
        @Value("${app.token-ttl}") Duration ttl) {
        this.users=users; this.sessions=sessions; this.passwords=passwords; this.encoder=encoder; this.ttl=ttl;
    }
    public static String normalizeEmail(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    @Transactional
    public AuthDtos.Token register(AuthDtos.Register request) {
        String email=normalizeEmail(request.email());
        if(users.existsByEmail(email)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Email is already registered");
        User user=users.saveAndFlush(new User(request.displayName().strip(),email,passwords.encode(request.password()),Role.EMPLOYEE));
        return issue(user);
    }
    @Transactional
    public AuthDtos.Token login(AuthDtos.Login request) {
        User user=users.findByEmail(normalizeEmail(request.email())).orElse(null);
        // Always perform BCrypt work, including unknown users.
        String hash=user==null ? DUMMY_HASH : user.getPasswordHash();
        boolean matches=passwords.matches(request.password(),hash);
        if(user==null || !matches || !user.isEnabled()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password");
        return issue(user);
    }
    private static final String DUMMY_HASH = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private AuthDtos.Token issue(User user) {
        Instant now=Instant.now(), expiry=now.plus(ttl);
        AuthSession session=sessions.save(new AuthSession(user.getId(),expiry));
        JwtClaimsSet claims=JwtClaimsSet.builder().issuer("university-itsm").subject(user.getId().toString())
            .issuedAt(now).expiresAt(expiry).id(session.getId().toString()).build();
        String token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        return new AuthDtos.Token(token,expiry,AuthDtos.Profile.of(user));
    }
    @Transactional
    public void logout(Jwt jwt) { sessions.findById(UUID.fromString(jwt.getId())).ifPresent(session -> session.revoke()); }
}
