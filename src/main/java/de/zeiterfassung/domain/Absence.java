package de.zeiterfassung.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "absence")
public class Absence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "absence_type", nullable = false)
    private AbsenceType type;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AbsenceStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_id")
    private AppUser decidedBy;

    @Column(name = "decision_comment", length = 500)
    private String decisionComment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Version
    private long version;

    protected Absence() {
    }

    public Absence(AppUser user, AbsenceType type, LocalDate startDate, LocalDate endDate, String note,
                   AbsenceStatus status, Instant createdAt) {
        this.user = user;
        this.type = type;
        this.startDate = startDate;
        this.endDate = endDate;
        this.note = note;
        this.status = status;
        this.createdAt = createdAt;
    }

    public void decide(boolean approved, AppUser decider, String comment, Instant when) {
        this.status = approved ? AbsenceStatus.APPROVED : AbsenceStatus.REJECTED;
        this.decidedBy = decider;
        this.decisionComment = comment;
        this.decidedAt = when;
    }

    public void cancel() { this.status = AbsenceStatus.CANCELLED; }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public AbsenceType getType() { return type; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getNote() { return note; }
    public AbsenceStatus getStatus() { return status; }
    public AppUser getDecidedBy() { return decidedBy; }
    public String getDecisionComment() { return decisionComment; }
    public Instant getCreatedAt() { return createdAt; }
}
