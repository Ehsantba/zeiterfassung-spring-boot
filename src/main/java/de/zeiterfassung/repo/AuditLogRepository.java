package de.zeiterfassung.repo;

import de.zeiterfassung.domain.AuditLog;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findAllByOrderByOccurredAtDescIdDesc(Pageable pageable);
}
