package com.aiplatform.service;

import com.aiplatform.exception.InsufficientCreditsException;
import com.aiplatform.model.CreditUsage;

import com.aiplatform.model.AiModelSelection;
import com.aiplatform.model.SubscriptionTier;
import com.aiplatform.model.User;
import com.aiplatform.repository.CreditUsageRepository;
import com.aiplatform.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SubscriptionService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreditUsageRepository creditUsageRepository;

    @Transactional
    public void checkAndDecrementCredits(Long userId, String modelId, String purpose) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        SubscriptionTier tier = user.getSubscriptionTier();
        AiModelSelection modelMetadata = AiModelSelection.fromModelId(modelId);
        
        // 1. Validation: Model Access (Tier Level OR Individual Purchase)
        boolean hasAccess = modelMetadata.getRequiredLevel() <= tier.getLevel() || 
                           user.getPurchasedModelIds().contains(modelId);
        
        if (!hasAccess) {
            throw new InsufficientCreditsException("Your plan (" + tier.name() + ") cannot access " + modelId + 
                ". Please upgrade or purchase this model individually.");
        }

        double cost = modelMetadata.getCreditCost();
        LocalDateTime now = LocalDateTime.now();

        // 2. Null-safe initialization
        if (user.getMonthlyCredits() == null) user.setMonthlyCredits(tier.getMonthlyLimit());
        if (user.getDailyCreditsUsed() == null) user.setDailyCreditsUsed(0.0);

        // 3. Monthly Reset
        if (user.getLastResetAt() == null || user.getLastResetAt().getMonth() != now.getMonth()) {
            user.setMonthlyCredits(tier.getMonthlyLimit());
            user.setDailyCreditsUsed(0.0);
            user.setLastResetAt(now);
        }

        // 4. Daily Reset
        if (user.getLastGenerationAt() != null && user.getLastGenerationAt().toLocalDate().isBefore(now.toLocalDate())) {
            user.setDailyCreditsUsed(0.0);
        }

        // 5. Validation: Balances
        if (user.getMonthlyCredits() < cost) {
            throw new InsufficientCreditsException("Insufficient credits (" + user.getMonthlyCredits() + "). Required: " + cost);
        }

        if (tier.getDailyLimit() > 0 && user.getDailyCreditsUsed() >= tier.getDailyLimit()) {
            throw new InsufficientCreditsException("Daily limit reached (" + tier.getDailyLimit() + ").");
        }

        // 6. Rate Limit (Cooldown)
        if (tier.getCooldownMinutes() > 0 && user.getLastGenerationAt() != null) {
            long minutesSinceLast = java.time.temporal.ChronoUnit.MINUTES.between(user.getLastGenerationAt(), now);
            if (minutesSinceLast < tier.getCooldownMinutes()) {
                throw new InsufficientCreditsException("Cooldown active. Wait " + (tier.getCooldownMinutes() - minutesSinceLast) + " min.");
            }
        }

        // 7. Deduct & Save
        user.setMonthlyCredits(user.getMonthlyCredits() - cost);
        user.setDailyCreditsUsed(user.getDailyCreditsUsed() + cost);
        user.setLastGenerationAt(now);
        userRepository.save(user);

        // 8. Log History
        creditUsageRepository.save(CreditUsage.builder()
                .user(user)
                .amount(-cost)
                .purpose(purpose != null ? purpose : "AI Content Generation (" + modelId + ")")
                .createdAt(now)
                .build());
    }

    @Transactional
    public void upgradePlan(Long userId, SubscriptionTier newTier) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        user.setSubscriptionTier(newTier);
        user.setMonthlyCredits(newTier.getMonthlyLimit());
        user.setDailyCreditsUsed(0.0);
        user.setLastResetAt(LocalDateTime.now());
        
        if (newTier != SubscriptionTier.FREE) {
            user.setSubscriptionExpiresAt(LocalDateTime.now().plusDays(30));
        } else {
            user.setSubscriptionExpiresAt(null);
        }
        
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public void checkImageStorageLimit(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        SubscriptionTier tier = user.getSubscriptionTier();
        int maxImages = tier.getMaxStoredImages();

        if (maxImages != -1) {
            int currentImages = user.getStoredImagesCount() == null ? 0 : user.getStoredImagesCount();
            if (currentImages >= maxImages) {
                throw new com.aiplatform.exception.StorageLimitExceededException(
                    "Image storage capacity is full (" + maxImages + " images). Please upgrade your plan to create more."
                );
            }
        }
    }

    @Transactional
    public void incrementImageStorage(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        int currentImages = user.getStoredImagesCount() == null ? 0 : user.getStoredImagesCount();
        user.setStoredImagesCount(currentImages + 1);
        userRepository.save(user);
    }
}
