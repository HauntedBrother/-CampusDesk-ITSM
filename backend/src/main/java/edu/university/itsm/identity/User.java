package edu.university.itsm.identity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="users")
public class User {
    @Id private UUID id;
    @Column(nullable=false) private String displayName;
    @Column(nullable=false, unique=true) private String email;
    @Column(nullable=false) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(name="role_name", nullable=false) private Role role;
    private boolean enabled = true;
    private Instant createdAt;
    protected User() {}
    public User(String displayName, String email, String passwordHash, Role role) {
        this.id=UUID.randomUUID(); this.displayName=displayName; this.email=email;
        this.passwordHash=passwordHash; this.role=role; this.createdAt=Instant.now();
    }
    public UUID getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public boolean isEnabled() { return enabled; }
}
