package com.boxy.boxy.core.config;

import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserPasswordSyncRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    public static final String DEFAULT_PASSWORD_HASH = "$2a$12$4Lfob5hG5Z.lDrlUr7PFV.y3lwyoNCh8NIodWBus9zpW2El4Qlo7O";

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            List<String> seedUsernames = List.of("superadmin", "admin", "seller", "warehouse", "cashier");
            List<User> users = userRepository.findAll();
            int updated = 0;
            for (User user : users) {
                boolean isSeedUser = seedUsernames.contains(user.getUsername()) || (user.getId() != null && user.getId() <= 4);
                if ((isSeedUser || user.getPasswordHash() == null) && !DEFAULT_PASSWORD_HASH.equals(user.getPasswordHash())) {
                    user.setPasswordHash(DEFAULT_PASSWORD_HASH);
                    userRepository.save(user);
                    updated++;
                }
            }
            if (updated > 0) {
                log.info("🔐 Synchronized password hash for {} user(s) to configured BCrypt hash", updated);
            }
        } catch (Exception e) {
            log.warn("Could not synchronize user password hashes on startup: {}", e.getMessage());
        }
    }
}
