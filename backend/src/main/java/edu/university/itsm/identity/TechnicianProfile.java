package edu.university.itsm.identity;
import jakarta.persistence.*;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
@Entity @Table(name="technician_profiles")
public class TechnicianProfile {
    @Id private UUID userId;
    private int capacity=5;
    private boolean available=true;
    @ElementCollection
    @CollectionTable(name="technician_skills",joinColumns=@JoinColumn(name="user_id"))
    @MapKeyColumn(name="category_id") @Column(name="proficiency",nullable=false)
    private Map<UUID,Integer> skills=new HashMap<>();
    protected TechnicianProfile() {}
    public TechnicianProfile(UUID userId, int capacity) {
        if (capacity<1) throw new IllegalArgumentException("Capacity must be positive");
        this.userId=userId; this.capacity=capacity;
    }
    public int getCapacity() { return capacity; }
    public boolean isAvailable() { return available; }
    public UUID getUserId(){return userId;}
    public Map<UUID,Integer> getSkills(){return Map.copyOf(skills);}
    public int proficiency(UUID categoryId){return skills.getOrDefault(categoryId,0);}
    public void configure(int capacity,boolean available,Map<UUID,Integer> skills) {
        if(capacity<1 || capacity>1000 || skills.values().stream().anyMatch(level -> level==null || level<1 || level>3)) throw new IllegalArgumentException("Invalid capacity or skill proficiency");
        this.capacity=capacity; this.available=available; this.skills.clear(); this.skills.putAll(skills);
    }
}
