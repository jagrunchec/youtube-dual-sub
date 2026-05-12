package com.dualsub.config;

import com.dualsub.model.Role;
import com.dualsub.model.User;
import com.dualsub.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Creates the default administrator account on first run (when no users exist).
 * Credentials are printed to the console — change the password immediately after login.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final String DEFAULT_ADMIN_EMAIL    = "admin@dualsub.local";
    private static final String DEFAULT_ADMIN_PASSWORD = "Admin123!";
    private static final int    DEFAULT_LIMITED_QUOTA  = 10;

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) return;   // already initialised

        User admin = new User();
        admin.setEmail(DEFAULT_ADMIN_EMAIL);
        admin.setPasswordHash(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD));
        admin.setFirstName("Admin");
        admin.setLastName("DualSub");
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        admin.setCreatedAt(LocalDateTime.now());
        userRepository.save(admin);

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║          COMPTE ADMINISTRATEUR CRÉÉ              ║");
        System.out.println("╠══════════════════════════════════════════════════╣");
        System.out.printf( "║  Email    : %-36s ║%n", DEFAULT_ADMIN_EMAIL);
        System.out.printf( "║  Password : %-36s ║%n", DEFAULT_ADMIN_PASSWORD);
        System.out.println("║                                                  ║");
        System.out.println("║  ⚠  Changez ce mot de passe après connexion !   ║");
        System.out.println("║                                                  ║");
        System.out.printf( "║  Quota LIMITED par défaut : %d vidéos/semaine    ║%n", DEFAULT_LIMITED_QUOTA);
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();
    }
}
