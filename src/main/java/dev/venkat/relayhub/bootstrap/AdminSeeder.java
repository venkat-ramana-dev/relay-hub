package dev.venkat.relayhub.bootstrap;

import dev.venkat.relayhub.enums.Role;
import dev.venkat.relayhub.entity.User;
import dev.venkat.relayhub.repository.UserRepository;
import dev.venkat.relayhub.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
@Profile({"dev", "local"})
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${relayhub.bootstrap.admin.email}")
    private String adminEmail;

    @Value("${relayhub.bootstrap.admin.password}")
    private String adminPassword;

    @Override
    public void run(String... args) {

        if (!userRepository.existsByRole(Role.ADMIN)) {
            log.info("No Admin found in database. Bootstrapping Root Admin...");

            String rawApiKey = UUID.randomUUID().toString().replace("-", "");

            String hashedApiKey = SecurityUtil.hashApiKey(rawApiKey);

            User rootAdmin = User.builder()
                    .name("System Admin")
                    .email(adminEmail)
                    .password(passwordEncoder.encode(adminPassword))
                    .role(Role.ADMIN)
                    .apiKey(hashedApiKey)
                    .build();

            userRepository.save(rootAdmin);

            log.warn("Root Admin created successfully: {}", adminEmail);
            log.warn("Generated admin API key (local/dev only): {}", rawApiKey);
        } else {
            log.info("Admin user already exists. Skipping bootstrap.");
        }
    }
}
