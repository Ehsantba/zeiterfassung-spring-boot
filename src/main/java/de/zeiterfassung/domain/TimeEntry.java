package de.zeiterfassung.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "time_entry")
public class TimeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes;

    @Column(length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryStatus status = EntryStatus.BOOKED;

    /** Optimistisches Locking: verhindert, dass zwei gleichzeitige Änderungen sich überschreiben. */
    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TimeEntry() {
    }

    public TimeEntry(AppUser user, LocalDate workDate, LocalTime startTime, LocalTime endTime,
                     int breakMinutes, String note, Instant createdAt) {
        this.user = user;
        this.workDate = workDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.breakMinutes = breakMinutes;
        this.note = note;
        this.createdAt = createdAt;
    }

    public void applyCorrection(LocalTime start, LocalTime end, int breakMinutes, String note) {
        this.startTime = start;
        this.endTime = end;
        this.breakMinutes = breakMinutes;
        this.note = note;
        this.status = EntryStatus.BOOKED;
    }

    public void markCorrectionPending() { this.status = EntryStatus.CORRECTION_PENDING; }
    public void markBooked() { this.status = EntryStatus.BOOKED; }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public LocalDate getWorkDate() { return workDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public int getBreakMinutes() { return breakMinutes; }
    public String getNote() { return note; }
    public EntryStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
