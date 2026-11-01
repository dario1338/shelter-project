package org.unibl.etf.shelterapi.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.unibl.etf.shelterapi.config.AdminProperties;
import org.unibl.etf.shelterapi.model.Role;
import org.unibl.etf.shelterapi.model.User;
import org.unibl.etf.shelterapi.repository.UserRepository;

import java.util.Locale;

/**
 * Creates the initial admin account at startup from ADMIN_EMAIL and ADMIN_PASSWORD.
 * It is idempotent and does nothing when the variables are missing or the account already exists.
 */
@Component
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final AdminProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminInitializer(AdminProperties properties, UserRepository userRepository,
                            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isBlank(properties.email()) || isBlank(properties.password())) {
            log.warn("ADMIN_EMAIL or ADMIN_PASSWORD is not set, skipping initial admin creation");
            return;
        }
        String email = properties.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            log.info("Initial admin already exists, nothing to do");
            return;
        }
        userRepository.save(new User(email, passwordEncoder.encode(properties.password()),
                "Admin", "Admin", Role.ADMIN));
        log.info("Initial admin account created");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
