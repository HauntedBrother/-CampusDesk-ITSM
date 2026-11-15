package edu.university.itsm.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.itsm.dashboard.DashboardController;
import edu.university.itsm.identity.*;
import edu.university.itsm.security.SecurityConfig;
import edu.university.itsm.shared.ApiErrors;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real JWT/BCrypt/Spring Security HTTP tests; repository persistence is tested separately. */
@WebMvcTest(controllers={AuthController.class,DashboardController.class}, properties={
    "app.jwt-secret=http-tests-only-secret-at-least-32-bytes", "app.token-ttl=PT1H"})
@Import({SecurityConfig.class,AuthService.class,ApiErrors.class})
class SecurityHttpTest {
    @MockitoBean UserRepository users;
    @MockitoBean SessionRepository sessions;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;
    private static final String PASSWORD="SecurePassword123!";
    private final AtomicReference<AuthSession> current=new AtomicReference<>();
    private User user;
    @BeforeEach void setup() {
        current.set(null);
        when(sessions.save(any())).thenAnswer(invocation -> { AuthSession session=invocation.getArgument(0); current.set(session); return session; });
        when(sessions.findById(any())).thenAnswer(invocation -> Optional.ofNullable(current.get()).filter(s -> s.getId().equals(invocation.getArgument(0))));
        setRole(Role.EMPLOYEE);
    }
    void setRole(Role role) {
        user=new User("Test User","test@uni.edu",passwords.encode(PASSWORD),role);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
    }
    String login() throws Exception {
        String body=mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("email",user.getEmail(),"password",PASSWORD))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }
    @ParameterizedTest @EnumSource(Role.class)
    void signedJwtEnforcesEveryRoleCombination(Role role) throws Exception {
        setRole(role); String token=login();
        for(Role target:Role.values()) mvc.perform(get("/api/dashboards/"+target.name().toLowerCase(Locale.ROOT)).header("Authorization","Bearer "+token))
            .andExpect(status().is(role==target ? 200 : 403));
    }
    @Test void loginReturnsCurrentProfile() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+login())).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }
    @Test void publicRegistrationHashesPasswordAndCannotEscalateRole() throws Exception {
        when(users.saveAndFlush(any())).thenAnswer(invocation -> {
            User created=invocation.getArgument(0);
            when(users.findById(created.getId())).thenReturn(Optional.of(created));
            return created;
        });
        mvc.perform(post("/api/auth/register").contentType("application/json")
            .content(json.writeValueAsString(Map.of("displayName","Student","email","STUDENT@uni.edu","password",PASSWORD,"role","ADMIN"))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.user.role").value("EMPLOYEE"))
            .andExpect(jsonPath("$.user.email").value("student@uni.edu"));
        var captured=org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captured.capture());
        assertNotEquals(PASSWORD,captured.getValue().getPasswordHash());
        assertTrue(passwords.matches(PASSWORD,captured.getValue().getPasswordHash()));
    }
    @Test void logoutRejectsPreviouslyValidJwt() throws Exception {
        String token=login();
        mvc.perform(post("/api/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isNoContent());
        assertFalse(current.get().isActive());
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }
    @Test void anonymousRequestRejected() throws Exception { mvc.perform(get("/api/dashboards/admin")).andExpect(status().isUnauthorized()); }
    @Test void tamperedSignatureRejected() throws Exception {
        String[] pieces=login().split("\\.");
        String changed=(pieces[2].startsWith("A") ? "B" : "A")+pieces[2].substring(1);
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+pieces[0]+"."+pieces[1]+"."+changed)).andExpect(status().isUnauthorized());
    }
    @Test void expiredSignedTokenRejected() throws Exception {
        login();
        JwtClaimsSet claims=JwtClaimsSet.builder().issuer("university-itsm").subject(user.getId().toString()).id(current.get().getId().toString())
            .issuedAt(Instant.now().minusSeconds(600)).expiresAt(Instant.now().minusSeconds(120)).build();
        String token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }
    @Test void invalidCredentialsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"email\":\"test@uni.edu\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }
    @Test void invalidInputRejectedBeforeRegistration() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{\"displayName\":\"\",\"email\":\"invalid\",\"password\":\"short\"}"))
            .andExpect(status().isBadRequest()); verify(users,never()).saveAndFlush(any());
    }
    @Test void missingPersistedSessionRejected() throws Exception {
        String token=login(); current.set(null);
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }
}
