package edu.university.itsm.incident;

import edu.university.itsm.catalog.*;
import edu.university.itsm.identity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity @Table(name="incidents")
public class Incident {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Version private long version;
    @Column(length=200,nullable=false) private String title;
    @Column(length=10000,nullable=false) private String description;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="reporter_id") private User reporter;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="service_id") private ITService service;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="category_id") private IncidentCategory category;
    private int impact;
    private int urgency;
    @Enumerated(EnumType.STRING) @Column(length=2) private IncidentPriority priority;
    private Integer priorityScore;
    private Integer priorityCriticality;
    @Column(length=500) private String assignmentIssue;
    @Embedded private SlaSnapshot sla=new SlaSnapshot();
    @Enumerated(EnumType.STRING) @Column(length=20,nullable=false) private IncidentStatus status=IncidentStatus.NEW;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="technician_id") private User technician;
    @Column(length=10000) private String resolution;
    @Column(length=2000) private String feedback;
    private Integer feedbackRating;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant acknowledgedAt;
    private Instant resolvedAt;
    private Instant verifiedAt;
    private Instant closedAt;
    protected Incident() {}
    public Incident(User reporter, String title, String description, ITService service, IncidentCategory category, int impact, int urgency) {
        this.reporter=reporter; createdAt=now(); update(title,description,service,category,impact,urgency);
    }
    void update(String title,String description,ITService service,IncidentCategory category,int impact,int urgency) {
        if (impact<1 || impact>3 || urgency<1 || urgency>3) throw new InvalidIncidentOperation("Impact and urgency must be between 1 and 3");
        this.title=title.strip(); this.description=description.strip(); this.service=service; this.category=category;
        this.impact=impact; this.urgency=urgency; touch();
    }
    void transition(IncidentStatus target) {
        IncidentStates.of(status).requireTransition(target);
        validatePrerequisites(target); status=target; markMilestone(target); touch();
    }
    void assign(User technician) {
        if (status!=IncidentStatus.CLASSIFIED && status!=IncidentStatus.ASSIGNED) throw new InvalidIncidentOperation("Assign only classified or assigned incidents");
        this.technician=technician;
        if(status==IncidentStatus.CLASSIFIED) transition(IncidentStatus.ASSIGNED);
        touch();
    }
    void resolve(String details) {
        if (details==null || details.isBlank()) throw new InvalidIncidentOperation("Resolution details are required");
        IncidentStates.of(status).requireTransition(IncidentStatus.RESOLVED);
        resolution=details.strip(); transition(IncidentStatus.RESOLVED);
    }
    void provideFeedback(int rating, String details) {
        if (status.ordinal()<IncidentStatus.RESOLVED.ordinal()) throw new InvalidIncidentOperation("Feedback is available after resolution");
        if (rating<1 || rating>5) throw new InvalidIncidentOperation("Rating must be between 1 and 5");
        feedbackRating=rating; feedback=details.strip(); touch();
    }
    void overrideTo(IncidentStatus target) {
        if(target==status) throw new InvalidIncidentOperation("Incident is already in this state");
        validatePrerequisites(target);
        if(target.ordinal()<IncidentStatus.ASSIGNED.ordinal()) technician=null;
        if(target.ordinal()<IncidentStatus.ACKNOWLEDGED.ordinal()) acknowledgedAt=null;
        if(target.ordinal()<IncidentStatus.RESOLVED.ordinal()) { resolution=null; resolvedAt=null; feedback=null; feedbackRating=null; }
        if(target.ordinal()<IncidentStatus.VERIFIED.ordinal()) verifiedAt=null;
        if(target!=IncidentStatus.CLOSED) closedAt=null;
        status=target; markMilestone(target); touch();
    }
    private void validatePrerequisites(IncidentStatus target) {
        if(target.ordinal()>=IncidentStatus.ASSIGNED.ordinal() && technician==null) throw new InvalidIncidentOperation("A technician must be assigned first");
        if(target.ordinal()>=IncidentStatus.RESOLVED.ordinal() && (resolution==null || resolution.isBlank())) throw new InvalidIncidentOperation("A resolution must be recorded first");
    }
    private void markMilestone(IncidentStatus target) {
        Instant now=now();
        if(target==IncidentStatus.ACKNOWLEDGED) acknowledgedAt=now;
        if(target==IncidentStatus.RESOLVED) resolvedAt=now;
        if(target==IncidentStatus.VERIFIED || target==IncidentStatus.CLOSED) { if(verifiedAt==null) verifiedAt=now; }
        if(target==IncidentStatus.CLOSED) closedAt=now;
    }
    // PostgreSQL timestamps retain microseconds. Keep returned and reloaded clocks identical.
    private static Instant now(){return Instant.now().truncatedTo(ChronoUnit.MICROS);}
    void touch() { updatedAt=now(); }
    void applyPriority(IncidentPriority priority,int score,int criticality){this.priority=priority; priorityScore=score; priorityCriticality=criticality; touch();}
    void assignmentIssue(String issue){assignmentIssue=issue;}
    public Long getId(){return id;}
    public String getReference(){return id==null ? null : "INC-%06d".formatted(id);}
    public long getVersion(){return version;}
    public String getTitle(){return title;}
    public String getDescription(){return description;}
    public User getReporter(){return reporter;}
    public ITService getService(){return service;}
    public IncidentCategory getCategory(){return category;}
    public int getImpact(){return impact;}
    public int getUrgency(){return urgency;}
    public IncidentPriority getPriority(){return priority;}
    public Integer getPriorityScore(){return priorityScore;}
    public Integer getPriorityCriticality(){return priorityCriticality;}
    public String getAssignmentIssue(){return assignmentIssue;}
    public SlaSnapshot getSla(){if(sla==null) sla=new SlaSnapshot(); return sla;}
    public IncidentStatus getStatus(){return status;}
    public User getTechnician(){return technician;}
    public String getResolution(){return resolution;}
    public String getFeedback(){return feedback;}
    public Integer getFeedbackRating(){return feedbackRating;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getUpdatedAt(){return updatedAt;}
    public Instant getAcknowledgedAt(){return acknowledgedAt;}
    public Instant getResolvedAt(){return resolvedAt;}
    public Instant getVerifiedAt(){return verifiedAt;}
    public Instant getClosedAt(){return closedAt;}
}
