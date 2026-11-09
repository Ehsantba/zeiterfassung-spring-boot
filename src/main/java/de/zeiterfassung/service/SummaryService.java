package de.zeiterfassung.service;

import de.zeiterfassung.domain.Absence;
import de.zeiterfassung.domain.AbsenceType;
import de.zeiterfassung.domain.AppUser;
import de.zeiterfassung.domain.TimeEntry;
import de.zeiterfassung.repo.AbsenceRepository;
import de.zeiterfassung.repo.TimeEntryRepository;
import de.zeiterfassung.service.Dto.DaySummary;
import de.zeiterfassung.service.Dto.SummaryResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SummaryService {

    private final TimeEntryRepository entries;
    private final AbsenceRepository absences;
    private final Clock clock;

    public SummaryService(TimeEntryRepository entries, AbsenceRepository absences, Clock clock) {
        this.entries = entries;
        this.absences = absences;
        this.clock = clock;
    }

    /**
     * Monatsübersicht mit Summen und Saldo.
     * Soll-Zeit: 8 Stunden an Werktagen (Mo-Fr). Feiertage (NRW) und genehmigte Abwesenheiten
     * (Urlaub, Krankheit, Sonstiges) haben kein Soll. Gezählt werden nur vergangene Tage und heute,
     * wenn bereits gebucht wurde.
     */
    public SummaryResponse month(AppUser user, YearMonth month) {
        LocalDate today = LocalDate.now(clock.withZone(WorkTime.ZONE));
        List<TimeEntry> list = entries.findByUserAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                user, month.atDay(1), month.atEndOfMonth());

        Map<LocalDate, AbsenceType> absent = new TreeMap<>();
        for (Absence a : absences.findApproved(user, month.atDay(1), month.atEndOfMonth())) {
            for (LocalDate d = a.getStartDate(); !d.isAfter(a.getEndDate()); d = d.plusDays(1)) {
                absent.putIfAbsent(d, a.getType());
            }
        }

        Map<LocalDate, long[]> perDay = new TreeMap<>(); // [minutes, count]
        for (TimeEntry e : list) {
            long[] v = perDay.computeIfAbsent(e.getWorkDate(), d -> new long[2]);
            v[0] += WorkTime.netMinutes(e.getWorkDate(), e.getStartTime(), e.getEndTime(), e.getBreakMinutes());
            v[1]++;
        }

        List<DaySummary> days = new ArrayList<>();
        long worked = 0;
        long target = 0;
        for (int d = 1; d <= month.lengthOfMonth(); d++) {
            LocalDate date = month.atDay(d);
            long[] v = perDay.getOrDefault(date, new long[2]);
            String holiday = HolidayCalendar.holidayName(date).orElse(null);
            AbsenceType absence = absent.get(date);
            boolean counts = date.isBefore(today) || (date.equals(today) && v[1] > 0);
            boolean targetDay = HolidayCalendar.isWorkday(date) && absence == null;
            long dayTarget = targetDay && counts ? WorkTime.DAILY_TARGET_MINUTES : 0;
            days.add(new DaySummary(date, v[0], dayTarget, (int) v[1], holiday, absence));
            worked += v[0];
            target += dayTarget;
        }
        return new SummaryResponse(month.toString(), days, worked, target, worked - target);
    }
}
