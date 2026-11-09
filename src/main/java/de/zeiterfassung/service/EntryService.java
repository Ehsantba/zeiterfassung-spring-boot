package de.zeiterfassung.service;

import de.zeiterfassung.domain.AppUser;
import de.zeiterfassung.domain.TimeEntry;
import de.zeiterfassung.repo.TimeEntryRepository;
import de.zeiterfassung.service.Dto.EntryRequest;
import de.zeiterfassung.service.Dto.EntryResponse;
import de.zeiterfassung.service.Dto.EntryResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EntryService {

    private final TimeEntryRepository entries;
    private final AuditService audit;
    private final Clock clock;

    public EntryService(TimeEntryRepository entries, AuditService audit, Clock clock) {
        this.entries = entries;
        this.audit = audit;
        this.clock = clock;
    }

    public EntryResult create(AppUser user, EntryRequest request) {
        List<String> warnings = validate(user, null, request.workDate(), request.startTime(),
                request.endTime(), request.breakMinutes());
        TimeEntry entry = new TimeEntry(user, request.workDate(), request.startTime(), request.endTime(),
                request.breakMinutes(), clean(request.note()), Instant.now(clock));
        entries.save(entry);
        audit.log("TimeEntry", entry.getId(), "CREATE", user.getEmail(), null, AuditService.describe(entry));
        return new EntryResult(EntryResponse.of(entry), warnings);
    }

    @Transactional(readOnly = true)
    public List<EntryResponse> list(AppUser user, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessRuleException("Das Enddatum liegt vor dem Startdatum.");
        }
        return entries.findByUserAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(user, from, to)
                .stream().map(EntryResponse::of).toList();
    }

    /**
     * Plausibilitätsprüfungen. Wirft bei harten Fehlern eine BusinessRuleException,
     * liefert weiche Hinweise (z. B. sehr langer Arbeitstag) als Warnungen zurück.
     *
     * @param excludeId Eintrag, der beim Überschneidungstest ignoriert wird (bei Korrekturen), sonst null
     */
    @Transactional(readOnly = true)
    public List<String> validate(AppUser user, Long excludeId, LocalDate date, LocalTime start, LocalTime end,
                                 int breakMinutes) {
        if (!end.isAfter(start)) {
            throw new BusinessRuleException("Das Ende muss nach dem Beginn liegen.");
        }
        if (date.isAfter(LocalDate.now(clock.withZone(WorkTime.ZONE)))) {
            throw new BusinessRuleException("Buchungen in der Zukunft sind nicht erlaubt.");
        }
        long gross = WorkTime.grossMinutes(date, start, end);
        if (breakMinutes > gross) {
            throw new BusinessRuleException("Die Pause darf nicht länger sein als die Arbeitszeit.");
        }
        long exclude = excludeId == null ? -1L : excludeId;
        if (!entries.findOverlapping(user, date, start, end, exclude).isEmpty()) {
            throw new BusinessRuleException("Die Zeit überschneidet sich mit einer bestehenden Buchung.");
        }

        List<String> warnings = new ArrayList<>();
        long otherNet = entries.findByUserAndWorkDate(user, date).stream()
                .filter(e -> excludeId == null || !e.getId().equals(excludeId))
                .mapToLong(e -> WorkTime.netMinutes(e.getWorkDate(), e.getStartTime(), e.getEndTime(), e.getBreakMinutes()))
                .sum();
        long dayTotal = otherNet + WorkTime.netMinutes(date, start, end, breakMinutes);
        if (dayTotal > WorkTime.LONG_DAY_WARNING_MINUTES) {
            warnings.add("Sehr langer Arbeitstag: mehr als 10 Stunden Nettoarbeitszeit an diesem Tag.");
        }
        return warnings;
    }

    static String clean(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
