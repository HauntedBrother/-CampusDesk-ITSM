package edu.university.itsm.incident.automation;
import jakarta.persistence.*;
@Entity @Table(name="automation_settings")
public class AutomationSettings {
    @Id private Integer id=1;
    @Enumerated(EnumType.STRING) @Column(length=20,nullable=false) private AssignmentMode strategy=AssignmentMode.SKILL_BASED;
    private boolean automaticAssignment=true;
    public AutomationSettings() {}
    public void configure(AssignmentMode strategy,boolean automaticAssignment) { this.strategy=strategy; this.automaticAssignment=automaticAssignment; }
    public AssignmentMode getStrategy(){return strategy;}
    public boolean isAutomaticAssignment(){return automaticAssignment;}
}
