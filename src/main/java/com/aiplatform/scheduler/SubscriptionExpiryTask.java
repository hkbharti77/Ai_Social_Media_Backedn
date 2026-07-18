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

    /**
     * Runs daily at 10:00 AM IST to send renewal reminders (3 days and 1 day before expiry).
     */
    @Scheduled(cron = "0 30 4 * * *") // 10:00 AM IST = 04:30 UTC
    @Transactional
    public void sendRenewalReminders() {
        LocalDateTime now = LocalDateTime.now();
        log.info("⏰ [SubscriptionTask] Checking renewal reminders at {}", now);

        // 3-day reminder window: expires between 2d23h and 3d1h from now
        LocalDateTime threeDayStart = now.plusDays(2).plusHours(23);
        LocalDateTime threeDayEnd   = now.plusDays(3).plusHours(1);

        // 1-day reminder window: expires between 23h and 25h from now
        LocalDateTime oneDayStart = now.plusHours(23);
        LocalDateTime oneDayEnd   = now.plusHours(25);

        List<User> allPremium = userRepository.findAllBySubscriptionTierNotAndSubscriptionExpiresAtAfter(
                SubscriptionTier.FREE, now);

        int reminded = 0;
        for (User user : allPremium) {
            if (user.getSubscriptionExpiresAt() == null) continue;
            LocalDateTime expiry = user.getSubscriptionExpiresAt();

            if (expiry.isAfter(threeDayStart) && expiry.isBefore(threeDayEnd)) {
                emailService.sendRenewalReminderEmail(user, 3);
                log.info("📧 [SubscriptionTask] 3-day reminder sent to {}", user.getEmail());
                reminded++;
            } else if (expiry.isAfter(oneDayStart) && expiry.isBefore(oneDayEnd)) {
                emailService.sendRenewalReminderEmail(user, 1);
                log.info("📧 [SubscriptionTask] 1-day reminder sent to {}", user.getEmail());
                reminded++;
            }
        }
        log.info("✅ [SubscriptionTask] Sent {} renewal reminders.", reminded);
    }
}
