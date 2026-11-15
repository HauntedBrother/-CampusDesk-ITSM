package edu.university.itsm.catalog;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="incident_categories")
public class IncidentCategory {
    @Id private UUID id;
    @Column(length=100, nullable=false) private String name;
    @Column(length=1000, nullable=false) private String description;
    private boolean active=true;
    protected IncidentCategory() {}
    public IncidentCategory(String name, String description) { id=UUID.randomUUID(); update(name,description,true); }
    public void update(String name, String description, boolean active) {
        this.name=name.strip(); this.description=description.strip(); this.active=active;
    }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
}
