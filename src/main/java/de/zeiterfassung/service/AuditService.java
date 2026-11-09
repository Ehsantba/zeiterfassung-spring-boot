package de.zeiterfassung.service;

import de.zeiterfassung.domain.AuditLog;
import de.zeiterfassung.domain.TimeEntry;
import de.zeiterfassung.repo.AuditLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Schreibt das Änderungsprotokoll in derselben Transaktion wie die fachliche Änderung. */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void log(String objectType, Long objectId, String action, String actor, String oldValue, String newValue) {
        repository.save(new AuditLog(objectType, objectId, action, actor, Instant.now(clock), oldValue, newValue));
    }

    public static String describe(TimeEntry e) {
        return describe(e.getWorkDate().toString(), e.getStartTime(), e.getEndTime(), e.getBreakMinutes(), e.getNote());
    }

    public static String describe(String date, LocalTime start, LocalTime end, int breakMinutes, String note) {
        String text = date + " " + start + "-" + end + ", Pause " + breakMinutes + " min";
        return note == null || note.isBlank() ? text : text + ", Notiz: " + note;
    }
}
