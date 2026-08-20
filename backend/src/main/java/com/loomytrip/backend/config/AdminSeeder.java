package com.loomytrip.backend.config;

import com.loomytrip.backend.entity.Admin;
import com.loomytrip.backend.entity.AdminRole;
import com.loomytrip.backend.repository.AdminRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the initial back-office {@code super_admin} on startup from credentials
 * supplied out-of-band — in production via Secrets Manager, injected by ECS as
 * {@code SEED_ADMIN_EMAIL} / {@code SEED_ADMIN_PASSWORD} (see terraform/rds.tf,
 * ecs.tf). The password is BCrypt-hashed here at runtime, so no admin password
 * ever lives in the repo. This replaces the old V2 SQL seed, whose plaintext
 * password hash was committed to git (removed by migration V13).
 * <p>
 * Runs after Flyway (which V13 uses to delete the compromised default admin) and
 * is a no-op when either value is blank (e.g. a prod-profile run with no secrets
 * wired) or when an admin with that email already exists — so it never overwrites
 * a rotated password and is safe to run on every boot.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final String seedEmail;
    private final String seedPassword;

    public AdminSeeder(
            AdminRepository adminRepository,
            PasswordEncoder passwordEncoder,
            @Value("${loomytrip.admin-seed.email:}") String seedEmail,
            @Value("${loomytrip.admin-seed.password:}") String seedPassword
    ) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedEmail = seedEmail;
        this.seedPassword = seedPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (seedEmail.isBlank() || seedPassword.isBlank()) {
            log.info("Admin seed skipped: SEED_ADMIN_EMAIL / SEED_ADMIN_PASSWORD not set.");
            return;
        }

        String email = seedEmail.trim().toLowerCase();
        if (adminRepository.findByEmail(email).isPresent()) {
            log.info("Admin seed skipped: admin '{}' already exists.", email);
            return;
        }

        Admin admin = new Admin();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(seedPassword));
        admin.setRole(AdminRole.super_admin);
        adminRepository.save(admin);

        log.info("Seeded initial super_admin '{}' from injected credentials.", email);
    }
}
