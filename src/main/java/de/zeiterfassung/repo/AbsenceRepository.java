package de.zeiterfassung.repo;

import de.zeiterfassung.domain.Absence;
import de.zeiterfassung.domain.AbsenceStatus;
import de.zeiterfassung.domain.AppUser;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AbsenceRepository extends JpaRepository<Absence, Long> {

    /** Aktive Abwesenheiten (offen oder genehmigt) im Zeitraum. Abgelehnte und zurückgezogene zählen nicht. */
    @Query("""
            select a from Absence a
            where a.user = :user and a.status in (de.zeiterfassung.domain.AbsenceStatus.PENDING,
                                                  de.zeiterfassung.domain.AbsenceStatus.APPROVED)
              and a.startDate <= :to and a.endDate >= :from
            order by a.startDate
            """)
    List<Absence> findActive(@Param("user") AppUser user, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select a from Absence a
            where a.user = :user and a.status = de.zeiterfassung.domain.AbsenceStatus.APPROVED
              and a.startDate <= :to and a.endDate >= :from
            order by a.startDate
            """)
    List<Absence> findApproved(@Param("user") AppUser user, @Param("from") LocalDate from, @Param("to") LocalDate to);

    List<Absence> findByUserOrderByStartDateDesc(AppUser user);

    List<Absence> findByStatusOrderByCreatedAtAsc(AbsenceStatus status);

    @Query("""
            select a from Absence a
            where a.status = :status and a.user.supervisor = :supervisor
            order by a.createdAt asc
            """)
    List<Absence> findByStatusForSupervisor(@Param("status") AbsenceStatus status,
                                            @Param("supervisor") AppUser supervisor);
}
