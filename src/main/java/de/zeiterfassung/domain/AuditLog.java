package de.zeiterfassung.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Append-only: Es gibt bewusst keine Setter und keine Update-/Delete-Pfade in der Anwendung. */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_type", nullable = false)
    private String objectType;

    @Column(name = "object_id")
    private Long objectId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String actor;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "old_value", length = 1000)
    private String oldValue;

    @Column(name = "new_value", length = 1000)
    private String newValue;

    protected AuditLog() {
    }

    public AuditLog(String objectType, Long objectId, String action, String actor,
                    Instant occurredAt, String oldValue, String newValue) {
        this.objectType = objectType;
        this.objectId = objectId;
        this.action = action;
        this.actor = actor;
        this.occurredAt = occurredAt;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public Long getId() { return id; }
    public String getObjectType() { return objectType; }
    public Long getObjectId() { return objectId; }
    public String getAction() { return action; }
    public String getActor() { return actor; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
}
