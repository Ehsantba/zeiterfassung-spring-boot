package de.zeiterfassung.config;

import de.zeiterfassung.domain.AppUser;
import de.zeiterfassung.domain.Role;
import de.zeiterfassung.repo.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Legt Demo-Benutzer an, damit die Anwendung nach dem Start sofort ausprobiert werden kann.
 * Nur aktiv bei app.seed-demo-data=true (SEED_DEMO_DATA=true). In Produktion ausgeschaltet lassen!
 */
@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    static final String DEMO_PASSWORD = "demo-passwort-1";

    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public DemoDataSeeder(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        String hash = encoder.encode(DEMO_PASSWORD);
        AppUser admin = users.save(new AppUser("Alex Admin", "admin@example.test", hash, Role.ADMIN, null));
        AppUser chef = users.save(new AppUser("Sam Vorgesetzt", "chef@example.test", hash, Role.SUPERVISOR, admin));
        users.save(new AppUser("Mia Muster", "mia@example.test", hash, Role.EMPLOYEE, chef));
        users.save(new AppUser("Ben Beispiel", "ben@example.test", hash, Role.EMPLOYEE, chef));
        log.warn("Demo-Benutzer angelegt (admin@, chef@, mia@, ben@example.test). Nicht in Produktion verwenden!");
    }
}
