package de.zeiterfassung.repo;

import de.zeiterfassung.domain.AppUser;
import de.zeiterfassung.domain.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<AppUser> findBySupervisorOrderByName(AppUser supervisor);
    List<AppUser> findBySupervisorAndActiveTrueOrderByName(AppUser supervisor);
    List<AppUser> findAllByOrderByName();
    List<AppUser> findByActiveTrueOrderByName();
    long countByRoleAndActiveTrue(Role role);
}
