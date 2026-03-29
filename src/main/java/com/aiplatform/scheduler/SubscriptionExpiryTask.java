package com.aiplatform.scheduler;

import com.aiplatform.model.User;
import com.aiplatform.model.SubscriptionTier;
import com.aiplatform.repository.UserRepository;
import com.aiplatform.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class SubscriptionExpiryTask {

    private final UserRepository userRepository;
    private final EmailService emailService;

    /**
     * Runs every hour to check for expired subscriptions.
     * In production, this can be set to run daily at midnight.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void checkExpiredSubscriptions() {
        LocalDateTime now = LocalDateTime.now();
        log.info("\u231b [SubscriptionTask] Checking for expired premium plans at {}", now);

        List<User> expiredUsers = userRepository.findAllBySubscriptionTierNotAndSubscriptionExpiresAtBefore(
                SubscriptionTier.FREE, now);

        if (expiredUsers.isEmpty()) {
            log.info("\u2705 [SubscriptionTask] No subscriptions due for downgrade.");
            return;
        }

        for (User user : expiredUsers) {
            log.warn("\u26a0\ufe0f [SubscriptionTask] Downgrading User {} (ID: {}) due to expiry", user.getEmail(), user.getId());
            
            // Downgrade to FREE
            user.setSubscriptionTier(SubscriptionTier.FREE);
            user.setMonthlyCredits(SubscriptionTier.FREE.getMonthlyLimit());
            user.setSubscriptionExpiresAt(null);
            
            userRepository.save(user);

            // Notify user
            emailService.sendPlanExpiredEmail(user);
        }

        log.info("\ud83d\udce1 [SubscriptionTask] Successfully processed {} downgrades.", expiredUsers.size());
    }
}
