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
    public void deductFixedCredits(Long userId, double cost, String purpose) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        LocalDateTime now = LocalDateTime.now();

        // Null-safe initialization
        if (user.getMonthlyCredits() == null) user.setMonthlyCredits(0.0);
        if (user.getBonusCredits() == null) user.setBonusCredits(0.0);
        if (user.getDailyCreditsUsed() == null) user.setDailyCreditsUsed(0.0);

        double totalAvailable = user.getMonthlyCredits() + user.getBonusCredits();

        // Validation: Balances
        if (totalAvailable < cost) {
            throw new InsufficientCreditsException("Insufficient credits (Total: " + totalAvailable + "). Required: " + cost);
        }

        // Deduct from Monthly first, then Bonus
        if (user.getMonthlyCredits() >= cost) {
            user.setMonthlyCredits(user.getMonthlyCredits() - cost);
        } else {
            double remainingCost = cost - user.getMonthlyCredits();
            user.setMonthlyCredits(0.0);
            user.setBonusCredits(user.getBonusCredits() - remainingCost);
        }

        user.setDailyCreditsUsed(user.getDailyCreditsUsed() + cost);
        user.setLastGenerationAt(now);
        userRepository.save(user);

        // Log History
        creditUsageRepository.save(CreditUsage.builder()
                .user(user)
                .amount(-cost)
                .purpose(purpose)
                .createdAt(now)
                .build());
    }

    @Transactional
    public void checkAndDecrementCredits(Long userId, String modelId, String purpose) {
        checkAndDecrementCredits(userId, modelId, 1, purpose);
    }

    @Transactional
    public void checkAndDecrementCredits(Long userId, String modelId, int count, String purpose) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        SubscriptionTier tier = user.getSubscriptionTier();
        if (tier == null) {
            tier = SubscriptionTier.FREE;
            user.setSubscriptionTier(tier); // Self-correcting for future reads
        }
        AiModelSelection modelMetadata = AiModelSelection.fromModelId(modelId);
        
        // 1. Validation: Model Access (Tier Level OR Individual Purchase)
        boolean hasAccess = modelMetadata.getRequiredLevel() <= tier.getLevel() || 
                           user.getPurchasedModelIds().contains(modelId);
        
        if (!hasAccess) {
            throw new InsufficientCreditsException("Your plan (" + tier.name() + ") cannot access " + modelId + 
                ". Please upgrade or purchase this model individually.");
        }

        double unitCost = modelMetadata.getCreditCost();
        double totalCost = unitCost * count;
        LocalDateTime now = LocalDateTime.now();

        // 2. Null-safe initialization
        if (user.getMonthlyCredits() == null) user.setMonthlyCredits(tier.getMonthlyLimit());
        if (user.getBonusCredits() == null) user.setBonusCredits(0.0);
        if (user.getDailyCreditsUsed() == null) user.setDailyCreditsUsed(0.0);

        // 3. Monthly Reset (Preserves bonusCredits)
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
        double totalAvailable = user.getMonthlyCredits() + user.getBonusCredits();
        if (totalAvailable < totalCost) {
            throw new InsufficientCreditsException("Insufficient credits (Total: " + totalAvailable + "). Required: " + totalCost);
        }

        if (tier.getDailyLimit() > 0 && (user.getDailyCreditsUsed() + totalCost) > tier.getDailyLimit()) {
            throw new InsufficientCreditsException("Daily limit would be exceeded. Remaining today: " + (tier.getDailyLimit() - user.getDailyCreditsUsed()));
        }

        // 6. Rate Limit (Cooldown) - only check if count > 0
        if (count > 0 && tier.getCooldownMinutes() > 0 && user.getLastGenerationAt() != null) {
            long minutesSinceLast = java.time.temporal.ChronoUnit.MINUTES.between(user.getLastGenerationAt(), now);
            if (minutesSinceLast < tier.getCooldownMinutes()) {
                throw new InsufficientCreditsException("Cooldown active. Wait " + (tier.getCooldownMinutes() - minutesSinceLast) + " min.");
            }
        }

        // 7. Deduct from Monthly first, then Bonus
        if (user.getMonthlyCredits() >= totalCost) {
            user.setMonthlyCredits(user.getMonthlyCredits() - totalCost);
        } else {
            double remainingCost = totalCost - user.getMonthlyCredits();
            user.setMonthlyCredits(0.0);
            user.setBonusCredits(user.getBonusCredits() - remainingCost);
        }

        user.setDailyCreditsUsed(user.getDailyCreditsUsed() + totalCost);
        user.setLastGenerationAt(now);
        userRepository.save(user);

        // 8. Log History
        creditUsageRepository.save(CreditUsage.builder()
                .user(user)
                .amount(-totalCost)
                .purpose(purpose != null ? purpose : "AI Content Generation (" + modelId + ") x" + count)
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

        // ── Add included video credits to wallet ─────────────────────────────
        // Credits are ADDED (not replaced) so existing purchased credits are preserved
        int liteCr     = newTier.getIncludedLiteCredits();
        int fastCr     = newTier.getIncludedFastCredits();
        int standardCr = newTier.getIncludedStandardCredits();

        if (liteCr > 0) {
            user.setVideoCreditLite((user.getVideoCreditLite() != null ? user.getVideoCreditLite() : 0) + liteCr);
        }
        if (fastCr > 0) {
            user.setVideoCreditFast((user.getVideoCreditFast() != null ? user.getVideoCreditFast() : 0) + fastCr);
        }
        if (standardCr > 0) {
            user.setVideoCreditStandard((user.getVideoCreditStandard() != null ? user.getVideoCreditStandard() : 0) + standardCr);
        }

        // ── Reset monthly video counter (old limit-based system) ─────────────
        user.setMonthlyVideoLimit(0); // No longer used — wallet-based now
        user.setVideosUsedThisMonth(0);
        user.setVideoResetDate(LocalDateTime.now());

        userRepository.save(user);

        org.slf4j.LoggerFactory.getLogger(SubscriptionService.class)
            .info("User {} upgraded to {}. Video credits added: {}L + {}F + {}S",
                userId, newTier.name(), liteCr, fastCr, standardCr);
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
    public void refundCredits(Long userId, double amount, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setMonthlyCredits((user.getMonthlyCredits() != null ? user.getMonthlyCredits() : 0.0) + amount);
        userRepository.save(user);

        creditUsageRepository.save(CreditUsage.builder()
                .user(user)
                .amount(amount)
                .purpose("REFUND: " + reason)
                .createdAt(LocalDateTime.now())
                .build());
    }

    public long calculateUpgradePrice(User user, SubscriptionTier targetTier) {
        if (user.getSubscriptionTier() == null || user.getSubscriptionTier() == SubscriptionTier.FREE) {
            return targetTier.getPriceInInr().longValue();
        }

        LocalDateTime now = LocalDateTime.now();
        if (user.getSubscriptionExpiresAt() == null || user.getSubscriptionExpiresAt().isBefore(now)) {
            return targetTier.getPriceInInr().longValue();
        }

        // 1. Calculate remaining days in current plan (max 30)
        long remainingDays = java.time.temporal.ChronoUnit.DAYS.between(now, user.getSubscriptionExpiresAt());
        if (remainingDays <= 0) return targetTier.getPriceInInr().longValue();
        if (remainingDays > 30) remainingDays = 30;

        // 2. Value of remaining days from current plan
        double currentPlanPrice = user.getSubscriptionTier().getPriceInInr();
        double valueOfRemainingDays = (currentPlanPrice / 30.0) * remainingDays;

        // 3. New plan price - Value of remaining current time
        double rawPrice = targetTier.getPriceInInr() - valueOfRemainingDays;
        
        // 4. Ensure we don't return negative or zero (minimum 1 INR for gateway validity)
        return Math.max(1L, Math.round(rawPrice));
    }

    @Transactional
    public void incrementImageStorage(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setStoredImagesCount((user.getStoredImagesCount() == null ? 0 : user.getStoredImagesCount()) + 1);
        userRepository.save(user);
    }

    @Transactional
    public void incrementVideoStorage(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setStoredVideosCount((user.getStoredVideosCount() == null ? 0 : user.getStoredVideosCount()) + 1);
        userRepository.save(user);
    }
}
