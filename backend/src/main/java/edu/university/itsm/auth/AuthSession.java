package edu.university.itsm.auth;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="auth_sessions")
public class AuthSession {
    @Id private UUID id;
    private UUID userId;
    private Instant expiresAt;
    private boolean revoked;
    protected AuthSession() {}
    public AuthSession(UUID userId, Instant expiresAt) { this.id=UUID.randomUUID(); this.userId=userId; this.expiresAt=expiresAt; }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public boolean isActive() { return !revoked && expiresAt.isAfter(Instant.now()); }
    public void revoke() { revoked=true; }
}
