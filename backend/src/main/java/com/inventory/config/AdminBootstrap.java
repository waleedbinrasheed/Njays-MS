package com.inventory.config;

import com.inventory.entity.AppUser;
import com.inventory.entity.Role;
import com.inventory.repository.AppUserRepository;
import com.inventory.repository.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Set;

/**
 * Creates the first administrator when the database has no users at all.
 * The password is temporary: the admin must change it at first login.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties.Bootstrap settings;

    public AdminBootstrap(AppUserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                          AppProperties properties) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.settings = properties.bootstrap();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        Role adminRole = roles.findByName(Role.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role missing; check Flyway migrations"));

        boolean generated = settings.adminPassword() == null || settings.adminPassword().isBlank();
        String password = generated ? randomPassword() : settings.adminPassword();

        AppUser admin = new AppUser(settings.adminUsername(), passwordEncoder.encode(password), "Administrator");
        admin.setMustChangePassword(true);
        admin.replaceRoles(Set.of(adminRole));
        users.save(admin);

        if (generated) {
            log.warn("""

                    ==================================================================
                      First administrator created
                        username: {}
                        temporary password: {}
                      You will be asked to change it at first sign-in.
                    ==================================================================""",
                    settings.adminUsername(), password);
        } else {
            log.warn("First administrator '{}' created with the configured temporary password", settings.adminUsername());
        }
    }

    private static String randomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        for (int i = 0; i < 4; i++) sb.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        return sb.toString();
    }
}
