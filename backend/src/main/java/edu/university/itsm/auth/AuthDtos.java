package edu.university.itsm.auth;
import edu.university.itsm.identity.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
public final class AuthDtos {
    private AuthDtos() {}
    public record Register(@NotBlank @Size(max=100) String displayName,
        @NotBlank @Email @Size(max=254) String email,
        @NotBlank @Size(min=12,max=72) @Pattern(regexp="^[\\x20-\\x7E]+$", message="Use printable ASCII characters") String password) {
        @Override public String toString() { return "Register[credentials=REDACTED]"; }
    }
    public record Login(@NotBlank @Email @Size(max=254) String email,
        @NotBlank @Size(max=72) @Pattern(regexp="^[\\x20-\\x7E]+$", message="Use printable ASCII characters") String password) {
        @Override public String toString() { return "Login[credentials=REDACTED]"; }
    }
    public record Profile(UUID id, String displayName, String email, Role role) {
        public static Profile of(User user) { return new Profile(user.getId(),user.getDisplayName(),user.getEmail(),user.getRole()); }
    }
    public record Token(String accessToken, Instant expiresAt, Profile user) {
        @Override public String toString() { return "Token[accessToken=REDACTED]"; }
    }
}
