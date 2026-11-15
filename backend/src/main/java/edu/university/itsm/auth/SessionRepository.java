package edu.university.itsm.auth;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface SessionRepository extends JpaRepository<AuthSession,UUID> {}
