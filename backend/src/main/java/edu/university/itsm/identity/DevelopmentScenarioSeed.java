package edu.university.itsm.identity;

import edu.university.itsm.catalog.*;
import edu.university.itsm.incident.*;
import edu.university.itsm.incident.automation.AssignmentEngine;
import edu.university.itsm.intelligence.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Real, explicitly development-only records. A version marker prevents replay on restart. */
@Component @Profile("dev") @Order(10)
public class DevelopmentScenarioSeed implements ApplicationRunner {
    private final UserRepository users;
    private final TechnicianProfileRepository profiles;
    private final ITServiceRepository services;
    private final IncidentCategoryRepository categories;
    private final CategoryRuleRepository rules;
    private final IncidentService incidents;
    private final IncidentRepository repository;
    private final IncidentAutomation automation;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final String password;
    public DevelopmentScenarioSeed(UserRepository users,TechnicianProfileRepository profiles,ITServiceRepository services,IncidentCategoryRepository categories,
        CategoryRuleRepository rules,IncidentService incidents,IncidentRepository repository,IncidentAutomation automation,JdbcTemplate jdbc,PasswordEncoder passwords,@Value("${DEV_SEED_PASSWORD}") String password) {
        this.users=users;this.profiles=profiles;this.services=services;this.categories=categories;this.rules=rules;this.incidents=incidents;this.repository=repository;this.automation=automation;this.jdbc=jdbc;this.passwords=passwords;this.password=password;
    }
    @Override @Transactional public void run(ApplicationArguments arguments) {
        automation.lock();
        if(jdbc.queryForObject("select count(*) from development_seed_versions where name='phase4-demo-v1'",Integer.class)>0)return;
        IncidentCategory network=category("Network","Connectivity, Wi-Fi and campus routing");
        IncidentCategory software=category("Software","Applications and installation problems");
        IncidentCategory authentication=category("Authentication","Accounts, passwords and sign-in");
        seedRules(network,List.of("wifi","wi fi","network","internet","connection"));
        seedRules(software,List.of("application","software","install","crash"));
        seedRules(authentication,List.of("password","login","sign in","account locked"));
        User networkTech=technician("network.tech@university.test","Network Engineer",5,true,Map.of(network.getId(),3,authentication.getId(),2));
        User softwareTech=technician("software.tech@university.test","Application Engineer",4,true,Map.of(software.getId(),3,network.getId(),1));
        technician("standby.tech@university.test","Standby Engineer",5,false,Map.of(network.getId(),2,software.getId(),1));
        User employee=users.findByEmail("employee@university.test").orElseThrow();User admin=users.findByEmail("admin@university.test").orElseThrow();
        ITService campus=service("Demo campus network",2),apps=service("Demo university applications",2);
        report(employee,admin,networkTech,campus,network,"Development sample: Faculty Wi-Fi instability","Wi-Fi intermittently disconnects in the faculty building after reconnecting.");
        report(employee,admin,networkTech,campus,network,"Development sample: Library network latency","Internet access in the library is slow during lecture breaks.");
        report(employee,admin,softwareTech,apps,software,"Development sample: Application installation failure","The university desktop application cannot complete software installation.");
        jdbc.update("insert into development_seed_versions(name) values ('phase4-demo-v1')");
    }
    private IncidentCategory category(String name,String description){return categories.findByNameIgnoreCase(name).orElseGet(() -> categories.saveAndFlush(new IncidentCategory(name,description)));}
    private ITService service(String name,int criticality){return services.findByNameIgnoreCase(name).orElseGet(() -> services.saveAndFlush(new ITService(name,"Explicit development scenario",criticality)));}
    private void seedRules(IncidentCategory category,List<String> words){for(String word:words)if(rules.findByCategoryIdAndKeyword(category.getId(),word).isEmpty())rules.save(new CategoryRule(category,word,5,true));}
    private User technician(String email,String name,int capacity,boolean available,Map<UUID,Integer> skills) {
        User user=users.findByEmail(email).orElseGet(() -> users.saveAndFlush(new User(name,email,passwords.encode(password),Role.TECHNICIAN)));
        if(user.getRole()!=Role.TECHNICIAN)throw new IllegalStateException("Development scenario email is already assigned to another role: "+email);
        if(!profiles.existsById(user.getId())){TechnicianProfile profile=new TechnicianProfile(user.getId(),capacity);profile.configure(capacity,available,skills);profiles.saveAndFlush(profile);}
        return user;
    }
    private void report(User employee,User admin,User target,ITService service,IncidentCategory category,String title,String description) {
        if(!service.isActive() || !category.isActive())return;
        IncidentDtos.Detail result=incidents.create(employee,new IncidentDtos.Input(title,description,service.getId(),category.getId(),1,1));
        if(result.incident().status()==IncidentStatus.NEW)result=incidents.classify(admin,result.incident().id());
        TechnicianProfile profile=profiles.findById(target.getId()).orElseThrow();
        if(profile.isAvailable() && profile.proficiency(category.getId())>0 && repository.countByTechnicianIdAndStatusIn(target.getId(),AssignmentEngine.ACTIVE)<profile.getCapacity())
            incidents.assign(admin,result.incident().id(),new IncidentDtos.Assignment(target.getId(),"Development scenario workload"));
    }
}
