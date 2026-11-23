package de.zeiterfassung.service;

import de.zeiterfassung.domain.AppUser;
import de.zeiterfassung.domain.AuditLog;
import de.zeiterfassung.domain.Role;
import de.zeiterfassung.repo.AppUserRepository;
import de.zeiterfassung.repo.AuditLogRepository;
import de.zeiterfassung.service.Dto.AuditResponse;
import de.zeiterfassung.service.Dto.CreateUserRequest;
import de.zeiterfassung.service.Dto.UpdateUserRequest;
import de.zeiterfassung.service.Dto.UserResponse;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    private final AppUserRepository users;
    private final AuditLogRepository auditLogs;
    private final AuditService audit;
    private final PasswordEncoder encoder;

    public UserService(AppUserRepository users, AuditLogRepository auditLogs, AuditService audit,
                       PasswordEncoder encoder) {
        this.users = users;
        this.auditLogs = auditLogs;
        this.audit = audit;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public UserResponse me(AppUser user) {
        return UserResponse.of(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> all() {
        return users.findAllByOrderByName().stream().map(UserResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> teamOf(AppUser actor) {
        List<AppUser> list = actor.getRole() == Role.ADMIN ? users.findByActiveTrueOrderByName()
                : users.findBySupervisorAndActiveTrueOrderByName(actor);
        return list.stream().map(UserResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public AppUser teamMember(AppUser actor, long userId) {
        AppUser target = users.findById(userId).orElseThrow(() -> new NotFoundException("Benutzer nicht gefunden."));
        boolean allowed = target.isActive() && (actor.getRole() == Role.ADMIN
                || (target.getSupervisor() != null && target.getSupervisor().getId().equals(actor.getId())));
        if (!allowed) {
            throw new NotFoundException("Benutzer nicht gefunden.");
        }
        return target;
    }

    public UserResponse create(AppUser actor, CreateUserRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("Diese E-Mail-Adresse ist bereits vergeben.");
        }
        AppUser supervisor = findSupervisor(request.supervisorId(), null);
        AppUser user = users.save(new AppUser(request.name().strip(), request.email().strip().toLowerCase(),
                encoder.encode(request.password()), request.role(), supervisor));
        audit.log("AppUser", user.getId(), "CREATE", actor.getEmail(), null,
                user.getEmail() + " (" + user.getRole() + ")");
        return UserResponse.of(user);
    }

    public UserResponse update(AppUser actor, long userId, UpdateUserRequest request) {
        AppUser target = findUser(userId);
        ensureNotSelf(actor, target);
        AppUser supervisor = findSupervisor(request.supervisorId(), target);

        if (target.isActive() && target.getRole() == Role.ADMIN && request.role() != Role.ADMIN
                && users.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw new ConflictException("Der letzte aktive Administrator kann nicht herabgestuft werden.");
        }
        if (request.role() == Role.EMPLOYEE && target.getRole() != Role.EMPLOYEE
                && !users.findBySupervisorAndActiveTrueOrderByName(target).isEmpty()) {
            throw new ConflictException("Weisen Sie zuerst die aktiven Teammitglieder einem anderen Vorgesetzten zu.");
        }

        String oldValue = describe(target);
        target.setRole(request.role());
        target.setSupervisor(supervisor);
        String newValue = describe(target);
        if (!oldValue.equals(newValue)) {
            audit.log("AppUser", target.getId(), "UPDATE", actor.getEmail(), oldValue, newValue);
        }
        return UserResponse.of(target);
    }

    public UserResponse setActive(AppUser actor, long userId, boolean active) {
        AppUser target = findUser(userId);
        ensureNotSelf(actor, target);
        if (target.isActive() == active) {
            return UserResponse.of(target);
        }
        if (!active && target.getRole() == Role.ADMIN && users.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw new ConflictException("Der letzte aktive Administrator kann nicht deaktiviert werden.");
        }
        if (!active && !users.findBySupervisorAndActiveTrueOrderByName(target).isEmpty()) {
            throw new ConflictException("Weisen Sie zuerst die aktiven Teammitglieder einem anderen Vorgesetzten zu.");
        }

        target.setActive(active);
        audit.log("AppUser", target.getId(), active ? "ACTIVATE" : "DEACTIVATE", actor.getEmail(),
                active ? "Deaktiviert" : "Aktiv", active ? "Aktiv" : "Deaktiviert");
        return UserResponse.of(target);
    }

    private AppUser findUser(long userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("Benutzer nicht gefunden."));
    }

    private AppUser findSupervisor(Long supervisorId, AppUser target) {
        if (supervisorId == null) {
            return null;
        }
        AppUser supervisor = users.findById(supervisorId)
                .orElseThrow(() -> new BusinessRuleException("Der Vorgesetzte existiert nicht."));
        if (!supervisor.isActive() || supervisor.getRole() == Role.EMPLOYEE) {
            throw new BusinessRuleException("Der Vorgesetzte muss aktiv sein und die Rolle SUPERVISOR oder ADMIN haben.");
        }
        if (target != null) {
            if (supervisor.getId().equals(target.getId())) {
                throw new BusinessRuleException("Ein Benutzer kann nicht sich selbst als Vorgesetzten haben.");
            }
            AppUser ancestor = supervisor;
            while (ancestor != null) {
                if (ancestor.getId().equals(target.getId())) {
                    throw new BusinessRuleException("Die Vorgesetzten-Zuordnung darf keinen Kreis bilden.");
                }
                ancestor = ancestor.getSupervisor();
            }
        }
        return supervisor;
    }

    private static void ensureNotSelf(AppUser actor, AppUser target) {
        if (actor.getId().equals(target.getId())) {
            throw new ForbiddenOperationException("Das eigene Benutzerkonto kann hier nicht geändert werden.");
        }
    }

    private static String describe(AppUser user) {
        return user.getEmail() + " (" + user.getRole() + ", Vorgesetzter: "
                + (user.getSupervisor() == null ? "–" : user.getSupervisor().getEmail()) + ")";
    }

    @Transactional(readOnly = true)
    public List<AuditResponse> auditTrail(int limit) {
        return auditLogs.findAllByOrderByOccurredAtDescIdDesc(PageRequest.of(0, Math.min(Math.max(limit, 1), 500)))
                .stream().map(AuditResponse::of).toList();
    }
}
