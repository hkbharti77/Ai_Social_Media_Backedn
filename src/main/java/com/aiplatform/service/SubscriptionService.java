package com.aiplatform.service;

import com.aiplatform.exception.InsufficientCreditsException;
import com.aiplatform.model.CreditUsage;
import com.aiplatform.model.SubscriptionTier;
import com.aiplatform.model.User;
import com.aiplatform.repository.CreditUsageRepository;
import com.aiplatform.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class SubscriptionService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreditUsageRepository creditUsageRepository;

    @Transactional
    public void checkAndDecrementCredits(Long userId, String purpose) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        SubscriptionTier tier = user.getSubscriptionTier();
        LocalDateTime now = LocalDateTime.now();

        // 0. Null-safe initialization for credits
        if (user.getMonthlyCredits() == null) {
            user.setMonthlyCredits(tier.getMonthlyLimit());
        }
        if (user.getDailyCreditsUsed() == null) {
            user.setDailyCreditsUsed(0);
        }

        // 1. Check Monthly Reset
        if (user.getLastResetAt() == null || user.getLastResetAt().getMonth() != now.getMonth()) {
            user.setMonthlyCredits(tier.getMonthlyLimit());
            user.setDailyCreditsUsed(0);
            user.setLastResetAt(now);
        }

        // 2. Check Daily Reset
        if (user.getLastGenerationAt() != null && user.getLastGenerationAt().toLocalDate().isBefore(now.toLocalDate())) {
            user.setDailyCreditsUsed(0);
        }

        // 3. Validation: Monthly Credits
        if (user.getMonthlyCredits() <= 0) {
            throw new InsufficientCreditsException("You have exhausted your monthly AI credits. Please upgrade your plan.");
        }

        // 4. Validation: Daily Limits
        if (tier.getDailyLimit() > 0 && user.getDailyCreditsUsed() >= tier.getDailyLimit()) {
            throw new InsufficientCreditsException("Daily limit reached (" + tier.getDailyLimit() + "). Try again tomorrow or upgrade.");
        }

        // 5. Validation: Cooldown / Rate Limit (1/hour for FREE)
        if (tier.getCooldownMinutes() > 0 && user.getLastGenerationAt() != null) {
            long minutesSinceLast = ChronoUnit.MINUTES.between(user.getLastGenerationAt(), now);
            if (minutesSinceLast < tier.getCooldownMinutes()) {
                long remaining = tier.getCooldownMinutes() - minutesSinceLast;
                throw new InsufficientCreditsException("Cooldown active. Please wait " + remaining + " more minutes or upgrade for instant generation.");
            }
        }

        // 6. Update usage
        user.setMonthlyCredits(user.getMonthlyCredits() - 1);
        user.setDailyCreditsUsed(user.getDailyCreditsUsed() + 1);
        user.setLastGenerationAt(now);
        
        userRepository.save(user);

        // 7. Log History
        creditUsageRepository.save(CreditUsage.builder()
                .user(user)
                .amount(-1)
                .purpose(purpose != null ? purpose : "AI Content Generation")
                .createdAt(now)
                .build());
    }

    @Transactional
    public void upgradePlan(Long userId, SubscriptionTier newTier) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        user.setSubscriptionTier(newTier);
        user.setMonthlyCredits(newTier.getMonthlyLimit());
        user.setDailyCreditsUsed(0);
        user.setLastResetAt(LocalDateTime.now());
        
        // 1. Set Expiry Date (30 days for all paid tiers)
        if (newTier != SubscriptionTier.FREE) {
            user.setSubscriptionExpiresAt(LocalDateTime.now().plusDays(30));
        } else {
            user.setSubscriptionExpiresAt(null);
        }
        
        userRepository.save(user);
    }
}
