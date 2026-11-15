package edu.university.itsm.auth;
import edu.university.itsm.identity.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock SessionRepository sessions;
    @Mock PasswordEncoder passwords;
    @Mock JwtEncoder encoder;
    AuthService service;
    @BeforeEach void setup() { service=new AuthService(users,sessions,passwords,encoder,Duration.ofHours(1)); }
    @Test void normalizesEmail() { assertEquals("person@university.edu",AuthService.normalizeEmail(" Person@University.EDU ")); }
    @Test void rejectsDuplicateRegistration() {
        when(users.existsByEmail("person@uni.edu")).thenReturn(true);
        var ex=assertThrows(ResponseStatusException.class,() -> service.register(new AuthDtos.Register("Person","PERSON@uni.edu","Password123456")));
        assertEquals(409,ex.getStatusCode().value()); verify(users,never()).saveAndFlush(any());
    }
    @Test void rejectsUnknownLoginWithPasswordWork() {
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        var ex=assertThrows(ResponseStatusException.class,() -> service.login(new AuthDtos.Login("a@uni.edu","wrong")));
        assertEquals(401,ex.getStatusCode().value()); verify(passwords).matches(eq("wrong"),anyString());
    }
    @Test void rejectsWrongPassword() {
        when(users.findByEmail(anyString())).thenReturn(Optional.of(new User("Person","a@uni.edu","hash",Role.ADMIN)));
        when(passwords.matches("wrong","hash")).thenReturn(false);
        assertThrows(ResponseStatusException.class,() -> service.login(new AuthDtos.Login("a@uni.edu","wrong")));
        verifyNoInteractions(encoder,sessions);
    }
    @Test void revocationInvalidatesSession() {
        var session=new AuthSession(java.util.UUID.randomUUID(),java.time.Instant.now().plusSeconds(60));
        assertTrue(session.isActive()); session.revoke(); assertFalse(session.isActive());
    }
    @Test void expiredSessionIsInactive() {
        assertFalse(new AuthSession(java.util.UUID.randomUUID(),java.time.Instant.now().minusSeconds(1)).isActive());
    }
    @Test void capacityMustBePositive() { assertThrows(IllegalArgumentException.class,() -> new TechnicianProfile(java.util.UUID.randomUUID(),0)); }
    @Test void dtoLoggingRedactsCredentials() {
        assertFalse(new AuthDtos.Login("a@uni.edu","secret-value").toString().contains("secret-value"));
        assertFalse(new AuthDtos.Register("Person","a@uni.edu","secret-value").toString().contains("secret-value"));
        assertFalse(new AuthDtos.Token("secret-token",java.time.Instant.now(),null).toString().contains("secret-token"));
    }
}
