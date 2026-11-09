package de.zeiterfassung.repo;

import de.zeiterfassung.domain.AppUser;
import de.zeiterfassung.domain.TimeEntry;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {

    List<TimeEntry> findByUserAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(AppUser user, LocalDate from, LocalDate to);

    List<TimeEntry> findByUserAndWorkDate(AppUser user, LocalDate workDate);

    /** Einträge desselben Tages, die sich mit [start, end) überschneiden (ohne den Eintrag excludeId). */
    @Query("""
            select e from TimeEntry e
            where e.user = :user and e.workDate = :date
              and e.startTime < :end and e.endTime > :start
              and e.id <> :excludeId
            """)
    List<TimeEntry> findOverlapping(@Param("user") AppUser user, @Param("date") LocalDate date,
                                    @Param("start") LocalTime start, @Param("end") LocalTime end,
                                    @Param("excludeId") long excludeId);
}
