package edu.university.itsm.identity;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component @Profile("dev") @org.springframework.core.annotation.Order(0)
public class DevelopmentSeed implements ApplicationRunner {
    private final UserRepository users;
    private final TechnicianProfileRepository technicians;
    private final PasswordEncoder passwords;
    private final String password;
    private final int capacity;
    public DevelopmentSeed(UserRepository users, TechnicianProfileRepository technicians, PasswordEncoder passwords,
        @Value("${DEV_SEED_PASSWORD}") String password, @Value("${TECHNICIAN_DEFAULT_CAPACITY:5}") int capacity) {
        if(password.length()<12 || password.length()>72 || !password.matches("[\\x20-\\x7E]+")) throw new IllegalArgumentException("Invalid development seed password");
        this.users=users; this.technicians=technicians; this.passwords=passwords; this.password=password; this.capacity=capacity;
    }
    @Override @Transactional public void run(ApplicationArguments args) {
        for(Role role:Role.values()) {
            String email=role.name().toLowerCase(java.util.Locale.ROOT)+"@university.test";
            User user=users.findByEmail(email).orElseGet(() -> users.saveAndFlush(new User(role.name()+" Demo",email,passwords.encode(password),role)));
            if(role==Role.TECHNICIAN && !technicians.existsById(user.getId())) technicians.save(new TechnicianProfile(user.getId(),capacity));
        }
    }
}
