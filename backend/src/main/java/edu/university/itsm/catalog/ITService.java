package edu.university.itsm.catalog;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="it_services")
public class ITService {
    @Id private UUID id;
    @Column(length=100, nullable=false) private String name;
    @Column(length=1000, nullable=false) private String description;
    private int businessCriticality;
    private boolean active=true;
    protected ITService() {}
    public ITService(String name, String description, int criticality) {
        id=UUID.randomUUID(); update(name,description,criticality,true);
    }
    public void update(String name, String description, int criticality, boolean active) {
        if (criticality<1 || criticality>3) throw new IllegalArgumentException("Criticality must be between 1 and 3");
        this.name=name.strip(); this.description=description.strip(); this.businessCriticality=criticality; this.active=active;
    }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getBusinessCriticality() { return businessCriticality; }
    public boolean isActive() { return active; }
}
