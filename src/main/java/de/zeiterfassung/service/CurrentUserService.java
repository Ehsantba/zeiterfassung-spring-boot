package de.zeiterfassung.service;

import de.zeiterfassung.domain.AppUser;
import de.zeiterfassung.repo.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final AppUserRepository users;

    public CurrentUserService(AppUserRepository users) {
        this.users = users;
    }

    public AppUser require(Authentication authentication) {
        return users.findByEmailIgnoreCase(authentication.getName())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new ForbiddenOperationException("Benutzer nicht gefunden oder deaktiviert."));
    }
}
