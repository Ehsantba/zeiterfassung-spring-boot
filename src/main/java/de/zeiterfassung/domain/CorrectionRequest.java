package de.zeiterfassung.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "correction_request")
public class CorrectionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id")
    private TimeEntry entry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_id")
    private AppUser requestedBy;

    @Column(name = "new_start_time", nullable = false)
    private LocalTime newStartTime;

    @Column(name = "new_end_time", nullable = false)
    private LocalTime newEndTime;

    @Column(name = "new_break_minutes", nullable = false)
    private int newBreakMinutes;

    @Column(name = "new_note", length = 500)
    private String newNote;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CorrectionStatus status = CorrectionStatus.PENDING;

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

    protected CorrectionRequest() {
    }

    public CorrectionRequest(TimeEntry entry, AppUser requestedBy, LocalTime newStartTime, LocalTime newEndTime,
                             int newBreakMinutes, String newNote, String reason, Instant createdAt) {
        this.entry = entry;
        this.requestedBy = requestedBy;
        this.newStartTime = newStartTime;
        this.newEndTime = newEndTime;
        this.newBreakMinutes = newBreakMinutes;
        this.newNote = newNote;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public void decide(boolean approved, AppUser decider, String comment, Instant when) {
        this.status = approved ? CorrectionStatus.APPROVED : CorrectionStatus.REJECTED;
        this.decidedBy = decider;
        this.decisionComment = comment;
        this.decidedAt = when;
    }

    public Long getId() { return id; }
    public TimeEntry getEntry() { return entry; }
    public AppUser getRequestedBy() { return requestedBy; }
    public LocalTime getNewStartTime() { return newStartTime; }
    public LocalTime getNewEndTime() { return newEndTime; }
    public int getNewBreakMinutes() { return newBreakMinutes; }
    public String getNewNote() { return newNote; }
    public String getReason() { return reason; }
    public CorrectionStatus getStatus() { return status; }
    public AppUser getDecidedBy() { return decidedBy; }
    public String getDecisionComment() { return decisionComment; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
}
