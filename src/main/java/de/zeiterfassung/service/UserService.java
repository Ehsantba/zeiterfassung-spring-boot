package de.zeiterfassung.service;

import de.zeiterfassung.domain.AppUser;
import de.zeiterfassung.domain.AuditLog;
import de.zeiterfassung.domain.Role;
import de.zeiterfassung.repo.AppUserRepository;
import de.zeiterfassung.repo.AuditLogRepository;
import de.zeiterfassung.service.Dto.AuditResponse;
import de.zeiterfassung.service.Dto.CreateUserRequest;
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
        List<AppUser> list = actor.getRole() == Role.ADMIN ? users.findAllByOrderByName()
                : users.findBySupervisorOrderByName(actor);
        return list.stream().map(UserResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public AppUser teamMember(AppUser actor, long userId) {
        AppUser target = users.findById(userId).orElseThrow(() -> new NotFoundException("Benutzer nicht gefunden."));
        boolean allowed = actor.getRole() == Role.ADMIN
                || (target.getSupervisor() != null && target.getSupervisor().getId().equals(actor.getId()));
        if (!allowed) {
            throw new NotFoundException("Benutzer nicht gefunden.");
        }
        return target;
    }

    public UserResponse create(AppUser actor, CreateUserRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("Diese E-Mail-Adresse ist bereits vergeben.");
        }
        AppUser supervisor = null;
        if (request.supervisorId() != null) {
            supervisor = users.findById(request.supervisorId())
                    .orElseThrow(() -> new BusinessRuleException("Der Vorgesetzte existiert nicht."));
            if (supervisor.getRole() == Role.EMPLOYEE) {
                throw new BusinessRuleException("Der Vorgesetzte muss die Rolle SUPERVISOR oder ADMIN haben.");
            }
        }
        AppUser user = users.save(new AppUser(request.name().strip(), request.email().strip().toLowerCase(),
                encoder.encode(request.password()), request.role(), supervisor));
        audit.log("AppUser", user.getId(), "CREATE", actor.getEmail(), null,
                user.getEmail() + " (" + user.getRole() + ")");
        return UserResponse.of(user);
    }

    @Transactional(readOnly = true)
    public List<AuditResponse> auditTrail(int limit) {
        return auditLogs.findAllByOrderByOccurredAtDescIdDesc(PageRequest.of(0, Math.min(Math.max(limit, 1), 500)))
                .stream().map(AuditResponse::of).toList();
    }
}
