package de.zeiterfassung.repo;

import de.zeiterfassung.domain.AppUser;
import de.zeiterfassung.domain.CorrectionRequest;
import de.zeiterfassung.domain.CorrectionStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CorrectionRequestRepository extends JpaRepository<CorrectionRequest, Long> {

    List<CorrectionRequest> findByRequestedByOrderByCreatedAtDesc(AppUser requestedBy);

    List<CorrectionRequest> findByStatusOrderByCreatedAtAsc(CorrectionStatus status);

    @Query("""
            select c from CorrectionRequest c
            where c.status = :status and c.requestedBy.supervisor = :supervisor
            order by c.createdAt asc
            """)
    List<CorrectionRequest> findByStatusForSupervisor(@Param("status") CorrectionStatus status,
                                                      @Param("supervisor") AppUser supervisor);
}
