package com.team21.uber.user;

import com.team21.uber.user.model.User;
import com.team21.uber.user.repository.UserRepository;
import com.team21.uber.user.model.Role;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SeedDataLoader {

    @Bean
    CommandLineRunner seedAdmin(UserRepository repo, PasswordEncoder encoder) {
        return args -> {
            System.out.println("🔥 Seeder started");

            // Ensure at least one ADMIN exists for role management testing
            if (!repo.existsByEmail("admin@example.com")) {

                User admin = new User();
                admin.setName("Admin");
                admin.setEmail("admin@example.com");
                admin.setPassword(encoder.encode("adminpass"));
                admin.setPhone("01000000000");

                // IMPORTANT: ADMIN only via seeding or role management endpoint
                admin.setRole(Role.ADMIN);

                repo.save(admin);

                System.out.println("✅ Admin user seeded");
            } else {
                System.out.println("ℹ️ Admin already exists");
            }

            // Optional (recommended): ensure system always has at least one RIDER test user
            if (!repo.existsByEmail("rider@example.com")) {

                User rider = new User();
                rider.setName("Test Rider");
                rider.setEmail("rider@example.com");
                rider.setPassword(encoder.encode("riderpass"));
                rider.setPhone("01000000001");

                // Default M2 rule consistency
                rider.setRole(Role.RIDER);

                repo.save(rider);

                System.out.println("✅ Rider test user seeded");
            }
        };
    }
}