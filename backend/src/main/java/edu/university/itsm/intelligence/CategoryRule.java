package edu.university.itsm.intelligence;
import edu.university.itsm.catalog.IncidentCategory;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="category_rules")
public class CategoryRule {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="category_id") private IncidentCategory category;
    @Column(length=80,nullable=false) private String keyword;
    private int weight;
    private boolean active;
    protected CategoryRule() {}
    public CategoryRule(IncidentCategory category,String keyword,int weight,boolean active){id=UUID.randomUUID();this.category=category;this.keyword=TextSimilarity.normalize(keyword);configure(weight,active);}
    public void configure(int weight,boolean active){if(weight<1 || weight>100)throw new IllegalArgumentException("Rule weight must be between 1 and 100");this.weight=weight;this.active=active;}
    public UUID getId(){return id;}
    public IncidentCategory getCategory(){return category;}
    public String getKeyword(){return keyword;}
    public int getWeight(){return weight;}
    public boolean isActive(){return active;}
}
