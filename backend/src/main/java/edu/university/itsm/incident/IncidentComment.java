package edu.university.itsm.incident;
import edu.university.itsm.identity.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="incident_comments")
public class IncidentComment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="incident_id") private Incident incident;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="author_id") private User author;
    @Column(length=5000,nullable=false) private String body;
    private boolean internal;
    private Instant createdAt;
    protected IncidentComment() {}
    public IncidentComment(Incident incident,User author,String body,boolean internal) {
        this.incident=incident; this.author=author; this.body=body.strip(); this.internal=internal; createdAt=Instant.now();
    }
    public Long getId(){return id;}
    public User getAuthor(){return author;}
    public String getBody(){return body;}
    public boolean isInternal(){return internal;}
    public Instant getCreatedAt(){return createdAt;}
}
