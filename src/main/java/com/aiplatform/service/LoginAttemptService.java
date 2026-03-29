package com.aiplatform.service;

import com.aiplatform.model.User;
import com.aiplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final UserRepository userRepository;

    public static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_TIME_HOURS = 24;

    @Transactional
    public void loginSucceeded(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            int failedAttempts = user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts();
            if (failedAttempts > 0) {
                user.setFailedLoginAttempts(0);
                user.setLockTime(null);
                userRepository.save(user);
            }
        });
    }

    @Transactional
    public void loginFailed(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            int failedAttempts = user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts();
            int newFailAttempts = failedAttempts + 1;
            user.setFailedLoginAttempts(newFailAttempts);

            if (newFailAttempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockTime(LocalDateTime.now().plusHours(LOCK_TIME_HOURS));
            }
            userRepository.save(user);
        });
    }

    public boolean isBlocked(User user) {
        if (user.getLockTime() == null) {
            return false;
        }
        return user.getLockTime().isAfter(LocalDateTime.now());
    }
}
