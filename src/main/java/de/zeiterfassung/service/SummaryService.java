package de.zeiterfassung.service;

import de.zeiterfassung.domain.AppUser;
import de.zeiterfassung.domain.TimeEntry;
import de.zeiterfassung.repo.TimeEntryRepository;
import de.zeiterfassung.service.Dto.DaySummary;
import de.zeiterfassung.service.Dto.SummaryResponse;
import java.time.Clock;
import java.time.DayOfWeek;
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
    private final Clock clock;

    public SummaryService(TimeEntryRepository entries, Clock clock) {
        this.entries = entries;
        this.clock = clock;
    }

    /**
     * Monatsübersicht mit Summen und Saldo.
     * Soll-Zeit: 8 Stunden an Werktagen (Mo-Fr), gezählt nur für vergangene Tage und für heute,
     * wenn bereits gebucht wurde. Feiertage und Urlaub sind nicht berücksichtigt (siehe README).
     */
    public SummaryResponse month(AppUser user, YearMonth month) {
        LocalDate today = LocalDate.now(clock.withZone(WorkTime.ZONE));
        List<TimeEntry> list = entries.findByUserAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                user, month.atDay(1), month.atEndOfMonth());

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
            boolean weekday = date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
            boolean counts = date.isBefore(today) || (date.equals(today) && v[1] > 0);
            long dayTarget = weekday && counts ? WorkTime.DAILY_TARGET_MINUTES : 0;
            days.add(new DaySummary(date, v[0], dayTarget, (int) v[1]));
            worked += v[0];
            target += dayTarget;
        }
        return new SummaryResponse(month.toString(), days, worked, target, worked - target);
    }
}
