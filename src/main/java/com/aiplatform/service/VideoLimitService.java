package com.aiplatform.service;

import com.aiplatform.exception.InsufficientCreditsException;
import com.aiplatform.model.CreditUsage;
import com.aiplatform.model.User;
import com.aiplatform.model.VeoModelSelection;
import com.aiplatform.repository.CreditUsageRepository;
import com.aiplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Manages the monthly video generation limits per subscription plan.
 *
 * Plan limits:
 *   Free     (₹0)    → 0 videos/month  (no video access)
 *   Creator  (₹299)  → 0 videos/month  (image-only plan)
 *   Standard (₹499)  → 5 videos/month  (Veo Lite only)
 *   Pro      (₹1,499)→ 10 videos/month (Veo Lite + Fast)
 *   Super Pro(₹2,999)→ 10 videos/month (all models)
 *
 * Extra video pricing (pay-per-video after limit):
 *   Standard: Lite ₹50
 *   Pro:      Lite ₹40 | Fast ₹120
 *   Super Pro:Lite ₹40 | Fast ₹120 | Standard ₹450
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VideoLimitService {

    private final UserRepository userRepository;
    private final CreditUsageRepository creditUsageRepository;

    // ── Monthly limits per tier ──────────────────────────────────────────────

    public static int getMonthlyVideoLimit(String tierName) {
        if (tierName == null) return 0;
        return switch (tierName.toLowerCase()) {
            case "standard"              -> 5;
            case "pro"                   -> 10;
            case "super pro", "superpro" -> 10;
            default                      -> 0; // Free, Creator
        };
    }

    // ── Extra video prices (INR) after monthly limit is exhausted ────────────

    /**
     * Returns the extra-video price in INR for a given tier + model combination.
     * Returns -1 if the tier cannot generate videos at all (Free / Creator).
     */
    public static int getExtraVideoPrice(String tierName, VeoModelSelection model) {
        if (tierName == null) return -1;
        return switch (tierName.toLowerCase()) {
            case "standard" -> switch (model) {
                case VEO_LITE     -> 50;
                default           -> -1; // Standard can only use Lite
            };
            case "pro" -> switch (model) {
                case VEO_LITE -> 40;
                case VEO_FAST -> 120;
                default       -> -1; // Pro cannot use Standard model
            };
            case "super pro", "superpro" -> switch (model) {
                case VEO_LITE     -> 40;
                case VEO_FAST     -> 120;
                case VEO_STANDARD -> 450;
            };
            default -> -1; // Free / Creator: no video
        };
    }

    // ── Core limit check ─────────────────────────────────────────────────────

    /**
     * Checks whether the user has remaining free videos this month.
     * Resets the counter automatically if a new billing month has started.
     *
     * @return true  → user still has free videos remaining
     *         false → monthly limit exhausted (extra charge applies)
     */
    @Transactional
    public boolean hasRemainingFreeVideos(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        resetIfNewMonth(user);
        userRepository.save(user);

        int limit = user.getMonthlyVideoLimit() != null ? user.getMonthlyVideoLimit() : 0;
        int used  = user.getVideosUsedThisMonth() != null ? user.getVideosUsedThisMonth() : 0;

        return used < limit;
    }

    /**
     * Returns how many free videos the user has left this month.
     */
    @Transactional
    public int getRemainingFreeVideos(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        resetIfNewMonth(user);
        userRepository.save(user);

        int limit = user.getMonthlyVideoLimit() != null ? user.getMonthlyVideoLimit() : 0;
        int used  = user.getVideosUsedThisMonth() != null ? user.getVideosUsedThisMonth() : 0;

        return Math.max(0, limit - used);
    }

    /**
     * Increments the monthly video usage counter after a successful generation.
     */
    @Transactional
    public void incrementVideoUsage(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        resetIfNewMonth(user);

        int used = user.getVideosUsedThisMonth() != null ? user.getVideosUsedThisMonth() : 0;
        user.setVideosUsedThisMonth(used + 1);
        userRepository.save(user);

        log.info("Video usage incremented for user {}. Used: {}/{}", userId,
                user.getVideosUsedThisMonth(), user.getMonthlyVideoLimit());
    }

    /**
     * Validates that the user can generate a video with the given model.
     * Throws {@link InsufficientCreditsException} if the tier has no video access at all.
     *
     * With the wallet-based system, this only checks tier/model access.
     * Actual credit deduction is handled by VideoCreditService.
     */
    @Transactional
    public boolean validateAndCheckVideoAccess(Long userId, String tierName, VeoModelSelection model) {
        // Tiers with zero video access
        if ("free".equalsIgnoreCase(tierName) || "creator".equalsIgnoreCase(tierName)) {
            throw new InsufficientCreditsException(
                "Video generation is not available on the " + tierName + " plan. " +
                "Please upgrade to Standard or higher.");
        }

        // Tier-model access check
        if (!model.isAccessibleByTier(tierName)) {
            throw new InsufficientCreditsException(
                "The " + model.getQualityTier() + " model is not available on your " + tierName + " plan.");
        }

        // Always return true — actual credit check done by VideoCreditService
        return true;
    }

    /**
     * Returns a summary map for the API response (remaining videos, extra price, etc.)
     */
    @Transactional
    public Map<String, Object> getVideoStatus(Long userId, String tierName, VeoModelSelection model) {
        int remaining = getRemainingFreeVideos(userId);
        int extraPrice = getExtraVideoPrice(tierName, model);
        int limit = getMonthlyVideoLimit(tierName);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        int used = user.getVideosUsedThisMonth() != null ? user.getVideosUsedThisMonth() : 0;

        return Map.of(
            "monthlyLimit",    limit,
            "videosUsed",      used,
            "videosRemaining", remaining,
            "extraVideoPrice", extraPrice,
            "canGenerateFree", remaining > 0,
            "extraPriceInr",   extraPrice > 0 ? "₹" + extraPrice : "N/A"
        );
    }

    // ── Plan upgrade helper ──────────────────────────────────────────────────

    /**
     * Called when a user upgrades/changes their subscription plan.
     * Resets the video counter and sets the new monthly limit.
     */
    @Transactional
    public void resetForNewPlan(Long userId, String newTierName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setMonthlyVideoLimit(getMonthlyVideoLimit(newTierName));
        user.setVideosUsedThisMonth(0);
        user.setVideoResetDate(LocalDateTime.now());
        userRepository.save(user);

        log.info("Video limits reset for user {} on plan {}. New limit: {}", userId, newTierName,
                user.getMonthlyVideoLimit());
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void resetIfNewMonth(User user) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastReset = user.getVideoResetDate();

        boolean needsReset = lastReset == null
                || lastReset.getMonth() != now.getMonth()
                || lastReset.getYear() != now.getYear();

        if (needsReset) {
            log.info("Monthly video counter reset for user {}", user.getId());
            user.setVideosUsedThisMonth(0);
            user.setVideoResetDate(now);

            // Ensure limit is in sync with current tier
            String tierName = user.getSubscriptionTier() != null
                    ? user.getSubscriptionTier().name().replace("_", " ")
                    : "free";
            // Normalise SUPER_PRO → "super pro"
            tierName = tierName.toLowerCase();
            user.setMonthlyVideoLimit(getMonthlyVideoLimit(tierName));
        }
    }
}
