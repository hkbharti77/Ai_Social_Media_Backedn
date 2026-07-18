package com.aiplatform.service;

import com.aiplatform.model.AiUsageLog;
import com.aiplatform.model.BrandVoiceMode;
import com.aiplatform.repository.AiUsageLogRepository;
import com.aiplatform.repository.UserRepository;
import com.aiplatform.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * AiBillingService — Single Responsibility: AI usage tracking and credit deductions.
 *
 * Owns all usage logging (token counts, action types) and personalization credit management.
 * This is the single source of truth for "who used what AI feature and how much it cost."
 */
@Service
@RequiredArgsConstructor
public class AiBillingService {

    private static final Logger logger = LoggerFactory.getLogger(AiBillingService.class);

    private final AiUsageLogRepository aiUsageLogRepository;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;

    /**
     * Logs AI usage to the database for audit, billing, and analytics.
     * Enforces mandatory userId — throws if missing.
     */
    public void logUsage(Long userId, String modelId, String actionType, Usage usage, String prompt, String resultUrl) {
        try {
            if (userId == null) {
                userId = SecurityUtils.getCurrentUserId();
            }

            if (userId == null) {
                logger.error("❌ SECURITY ALERT: Attempted to log AI usage without a valid userId. Action: {}", actionType);
                throw new RuntimeException("Authentication Required for AI Usage");
            }

            final Long finalUserId = userId;
            final String finalPrompt = prompt;
            final String finalResultUrl = resultUrl;

            userRepository.findById(finalUserId).ifPresent(user -> {
                AiUsageLog log = AiUsageLog.builder()
                        .user(user)
                        .modelId(modelId)
                        .actionType(actionType)
                        .promptTokens(usage != null ? (int) usage.getPromptTokens() : 0)
                        .completionTokens(usage != null ? (int) usage.getCompletionTokens() : 0)
                        .totalTokens(usage != null ? (int) usage.getTotalTokens() : 0)
                        .prompt(finalPrompt)
                        .resultUrl(finalResultUrl)
                        .createdAt(LocalDateTime.now())
                        .build();
                aiUsageLogRepository.save(log);
            });
        } catch (Exception e) {
            logger.error("❌ Failed to log AI usage: {}", e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("Authentication Required")) throw e;
        }
    }

    /**
     * Deducts credits based on Brand Voice personalization mode.
     * STYLE_DNA costs 2 credits, FULL_CONTEXT costs 5 credits.
     */
    public void deductPersonalizationCredits(Long userId, String modeStr) {
        BrandVoiceMode mode = BrandVoiceMode.NONE;
        try {
            if (modeStr != null) mode = BrandVoiceMode.valueOf(modeStr.toUpperCase());
        } catch (Exception e) {
            // Fallback to NONE
        }

        if (mode == BrandVoiceMode.STYLE_DNA) {
            subscriptionService.deductFixedCredits(userId, 2.0, "AI Personalization: Style DNA");
        } else if (mode == BrandVoiceMode.FULL_CONTEXT) {
            subscriptionService.deductFixedCredits(userId, 5.0, "AI Personalization: Full Context");
        }
    }
}
