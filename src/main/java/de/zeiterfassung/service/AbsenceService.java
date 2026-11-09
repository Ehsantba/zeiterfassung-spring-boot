package de.zeiterfassung.service;

import de.zeiterfassung.domain.*;
import de.zeiterfassung.repo.AbsenceRepository;
import de.zeiterfassung.repo.TimeEntryRepository;
import de.zeiterfassung.service.Dto.*;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Abwesenheiten: Urlaub muss vom Vorgesetzten genehmigt werden, Krankheit und Sonstiges gelten sofort.
 * Wie bei den Buchungen wird nichts gelöscht: Zurückgezogene Einträge bekommen den Status CANCELLED
 * und jeder Schritt steht im Änderungsprotokoll.
 */
@Service
@Transactional
public class AbsenceService {

    private static final int MAX_DAYS = 366;

    private final AbsenceRepository absences;
    private final TimeEntryRepository entries;
    private final AuditService audit;
    private final Clock clock;
    private final int vacationDaysPerYear;

    public AbsenceService(AbsenceRepository absences, TimeEntryRepository entries, AuditService audit, Clock clock,
                          @Value("${app.vacation-days-per-year:30}") int vacationDaysPerYear) {
        this.absences = absences;
        this.entries = entries;
        this.audit = audit;
        this.clock = clock;
        this.vacationDaysPerYear = vacationDaysPerYear;
    }

    public AbsenceResponse create(AppUser user, AbsenceRequest request) {
        LocalDate start = request.startDate();
        LocalDate end = request.endDate();
        if (end.isBefore(start)) {
            throw new BusinessRuleException("Das Ende darf nicht vor dem Beginn liegen.");
        }
        if (start.plusDays(MAX_DAYS).isBefore(end)) {
            throw new BusinessRuleException("Eine Abwesenheit darf höchstens ein Jahr umfassen.");
        }
        if (HolidayCalendar.countWorkdays(start, end) == 0) {
            throw new BusinessRuleException("Der Zeitraum enthält keinen Arbeitstag (Wochenende oder Feiertag).");
        }
        if (!absences.findActive(user, start, end).isEmpty()) {
            throw new BusinessRuleException("Im Zeitraum gibt es bereits eine Abwesenheit.");
        }
        if (!entries.findByUserAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(user, start, end).isEmpty()) {
            throw new BusinessRuleException("Im Zeitraum gibt es bereits Zeitbuchungen.");
        }
        if (request.type() == AbsenceType.VACATION) {
            checkVacationBudget(user, start, end);
        }

        AbsenceStatus status = request.type() == AbsenceType.VACATION ? AbsenceStatus.PENDING : AbsenceStatus.APPROVED;
        Absence absence = absences.save(new Absence(user, request.type(), start, end,
                EntryService.clean(request.note()), status, Instant.now(clock)));
        audit.log("Absence", absence.getId(), "CREATE", user.getEmail(), null, describe(absence));
        return AbsenceResponse.of(absence);
    }

    /** Urlaubsanspruch pro Kalenderjahr: Genehmigte und beantragte Tage zählen, damit nicht überbucht wird. */
    private void checkVacationBudget(AppUser user, LocalDate start, LocalDate end) {
        for (int year = start.getYear(); year <= end.getYear(); year++) {
            LocalDate from = LocalDate.of(year, 1, 1).isBefore(start) ? start : LocalDate.of(year, 1, 1);
            LocalDate to = LocalDate.of(year, 12, 31).isAfter(end) ? end : LocalDate.of(year, 12, 31);
            int requested = HolidayCalendar.countWorkdays(from, to);
            VacationBalance balance = balance(user, year);
            if (requested > balance.remaining()) {
                throw new BusinessRuleException("Nicht genug Urlaub für " + year + ": beantragt " + requested
                        + " Tage, verfügbar " + balance.remaining() + ".");
            }
        }
    }

    @Transactional(readOnly = true)
    public VacationBalance balance(AppUser user, int year) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        int approved = 0;
        int pending = 0;
        for (Absence a : absences.findActive(user, from, to)) {
            if (a.getType() != AbsenceType.VACATION) {
                continue;
            }
            LocalDate s = a.getStartDate().isBefore(from) ? from : a.getStartDate();
            LocalDate e = a.getEndDate().isAfter(to) ? to : a.getEndDate();
            int days = HolidayCalendar.countWorkdays(s, e);
            if (a.getStatus() == AbsenceStatus.APPROVED) {
                approved += days;
            } else {
                pending += days;
            }
        }
        return new VacationBalance(year, vacationDaysPerYear, approved, pending,
                Math.max(0, vacationDaysPerYear - approved - pending));
    }

    @Transactional(readOnly = true)
    public AbsenceOverview overview(AppUser user, int year) {
        List<AbsenceResponse> list = absences.findByUserOrderByStartDateDesc(user).stream()
                .filter(a -> a.getStartDate().getYear() == year || a.getEndDate().getYear() == year)
                .map(AbsenceResponse::of).toList();
        return new AbsenceOverview(balance(user, year), list);
    }

    public AbsenceResponse cancel(AppUser user, long id) {
        Absence absence = absences.findById(id)
                .filter(a -> a.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new NotFoundException("Abwesenheit nicht gefunden."));
        if (absence.getStatus() != AbsenceStatus.PENDING && absence.getStatus() != AbsenceStatus.APPROVED) {
            throw new ConflictException("Diese Abwesenheit ist bereits abgeschlossen.");
        }
        LocalDate today = LocalDate.now(clock.withZone(WorkTime.ZONE));
        if (absence.getStatus() == AbsenceStatus.APPROVED && !absence.getStartDate().isAfter(today)
                && absence.getType() == AbsenceType.VACATION) {
            throw new BusinessRuleException("Bereits begonnener Urlaub kann nicht mehr zurückgezogen werden.");
        }
        String before = describe(absence);
        absence.cancel();
        audit.log("Absence", absence.getId(), "CANCEL", user.getEmail(), before, describe(absence));
        return AbsenceResponse.of(absence);
    }

    @Transactional(readOnly = true)
    public List<AbsenceResponse> pendingFor(AppUser actor) {
        List<Absence> list = actor.getRole() == Role.ADMIN
                ? absences.findByStatusOrderByCreatedAtAsc(AbsenceStatus.PENDING)
                : absences.findByStatusForSupervisor(AbsenceStatus.PENDING, actor);
        return list.stream().map(AbsenceResponse::of).toList();
    }

    public AbsenceResponse decide(AppUser actor, long id, DecisionRequest decision) {
        Absence absence = absences.findById(id).orElseThrow(() -> new NotFoundException("Abwesenheit nicht gefunden."));
        AppUser requester = absence.getUser();
        boolean allowed = actor.getRole() == Role.ADMIN
                || (requester.getSupervisor() != null && requester.getSupervisor().getId().equals(actor.getId()));
        if (!allowed) {
            throw new NotFoundException("Abwesenheit nicht gefunden.");
        }
        if (actor.getId().equals(requester.getId())) {
            throw new ForbiddenOperationException("Eigene Anträge dürfen nicht selbst entschieden werden.");
        }
        if (absence.getStatus() != AbsenceStatus.PENDING) {
            throw new ConflictException("Der Antrag wurde bereits entschieden oder zurückgezogen.");
        }
        String comment = EntryService.clean(decision.comment());
        if (!decision.approve() && comment == null) {
            throw new BusinessRuleException("Bei einer Ablehnung ist ein Kommentar erforderlich.");
        }
        if (decision.approve() && !entries.findByUserAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                requester, absence.getStartDate(), absence.getEndDate()).isEmpty()) {
            throw new BusinessRuleException("Im Zeitraum wurden inzwischen Zeiten gebucht. Genehmigung nicht möglich.");
        }
        String before = describe(absence);
        absence.decide(decision.approve(), actor, comment, Instant.now(clock));
        audit.log("Absence", absence.getId(), decision.approve() ? "APPROVE" : "REJECT", actor.getEmail(), before,
                describe(absence) + (comment == null ? "" : " | Kommentar: " + comment));
        return AbsenceResponse.of(absence);
    }

    @Transactional(readOnly = true)
    public List<HolidayResponse> holidays(int year) {
        return HolidayCalendar.holidays(year).entrySet().stream()
                .map(e -> new HolidayResponse(e.getKey(), e.getValue())).toList();
    }

    private static String describe(Absence a) {
        return a.getType() + " " + a.getStartDate() + " bis " + a.getEndDate() + " (" + a.getStatus() + ")"
                + (a.getNote() == null ? "" : ", Notiz: " + a.getNote());
    }
}
