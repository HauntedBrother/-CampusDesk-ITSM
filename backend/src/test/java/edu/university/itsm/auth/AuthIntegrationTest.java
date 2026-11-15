package edu.university.itsm.auth;
import com.fasterxml.jackson.databind.*;
import edu.university.itsm.identity.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"app.jwt-secret=integration-tests-only-secret-at-least-32-bytes","spring.profiles.active=test","app.sla.monitor-enabled=false"})
@AutoConfigureMockMvc @Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class AuthIntegrationTest {
    @Container static PostgreSQLContainer<?> db=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",db::getJdbcUrl); registry.add("spring.datasource.username",db::getUsername); registry.add("spring.datasource.password",db::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    private static final String PASSWORD="AsecurePassword123!";
    String login(String email) throws Exception {
        String body=mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("email",email,"password",PASSWORD))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }
    @Test void registrationPersistsHashedEmployeeAndIgnoresRoleInjection() throws Exception {
        String email=UUID.randomUUID()+"@uni.edu";
        mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsString(Map.of("displayName","Student","email",email,"password",PASSWORD,"role","ADMIN"))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.user.role").value("EMPLOYEE"));
        User stored=users.findByEmail(email).orElseThrow();
        assertNotEquals(PASSWORD,stored.getPasswordHash()); assertTrue(passwords.matches(PASSWORD,stored.getPasswordHash()));
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+login(email))).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
    }
    @ParameterizedTest @EnumSource(Role.class)
    void roleCanOnlyAccessOwnDashboard(Role role) throws Exception {
        String email=UUID.randomUUID()+"@uni.edu";
        users.saveAndFlush(new User("Test",email,passwords.encode(PASSWORD),role));
        String token=login(email);
        for(Role target:Role.values()) mvc.perform(get("/api/dashboards/"+target.name().toLowerCase(Locale.ROOT)).header("Authorization","Bearer "+token))
            .andExpect(status().is(target==role ? 200 : 403));
    }
    @Test void logoutRevokesToken() throws Exception {
        String email=UUID.randomUUID()+"@uni.edu";
        users.saveAndFlush(new User("Test",email,passwords.encode(PASSWORD),Role.EMPLOYEE));
        String token=login(email);
        mvc.perform(post("/api/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }
    @Test void anonymousCannotAccessDashboard() throws Exception { mvc.perform(get("/api/dashboards/admin")).andExpect(status().isUnauthorized()); }
    @Test void invalidInputRejected() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{\"displayName\":\"\",\"email\":\"bad\",\"password\":\"short\"}")).andExpect(status().isBadRequest());
    }
    @Test void malformedTokenRejected() throws Exception { mvc.perform(get("/api/auth/me").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized()); }
    @Test void invalidLoginRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"email\":\"missing@uni.edu\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized());
    }
    @Test void duplicateEmailRejected() throws Exception {
        String email=UUID.randomUUID()+"@uni.edu";
        users.saveAndFlush(new User("Test",email,passwords.encode(PASSWORD),Role.EMPLOYEE));
        mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsString(Map.of("displayName","Student","email",email,"password",PASSWORD))))
            .andExpect(status().isConflict());
    }
}
