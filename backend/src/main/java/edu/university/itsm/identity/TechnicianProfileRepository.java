package edu.university.itsm.identity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface TechnicianProfileRepository extends JpaRepository<TechnicianProfile,UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from TechnicianProfile p where p.userId = :id")
    java.util.Optional<TechnicianProfile> lockById(@org.springframework.data.repository.query.Param("id") UUID id);
}
