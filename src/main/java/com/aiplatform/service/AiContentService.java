package com.aiplatform.service;

import com.aiplatform.dto.AiRequest;
import com.aiplatform.dto.ContentGenerationDtos.*;
import com.aiplatform.model.AiModelSelection;
import com.aiplatform.model.BusinessProfile;
import com.aiplatform.model.User;
import com.aiplatform.repository.UserRepository;
import com.aiplatform.util.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiContentService {
    private static final Logger logger = LoggerFactory.getLogger(AiContentService.class);

    private final AiOrchestrator aiOrchestrator;
    private final SubscriptionService subscriptionService;
    private final DistributedLockService lockService;
    private final UserRepository userRepository;
    private final AiMediaService aiMediaService;
    private final AiCacheService aiCacheService;
    private final AiSecurityService aiSecurityService;
    private final AiBillingService aiBillingService;
    private final AiTierService aiTierService;
    private final ProviderRouter providerRouter;

    public GeneratedPost generatePost(BusinessProfile bp, String userCmd, Long userId, String modelId, PostGenerationRequest request, boolean skipCreditDeduction) {
        String finalModelId = providerRouter.route(AiRequest.builder()
                .userId(userId)
                .modelId(modelId)
                .actionType("POST_GENERATION")
                .build()).selectedModelId();

        if (request != null && request.getAspectRatio() != null && !request.getAspectRatio().isEmpty()) {
            try {
                bp = (BusinessProfile) bp.clone();
                bp.setAspectRatio(request.getAspectRatio());
            } catch (CloneNotSupportedException e) {
                logger.error("❌ Failed to clone profile: {}", e.getMessage());
            }
        }
        
        if (!skipCreditDeduction) {
            lockService.executeWithLock("credits:" + userId, Duration.ofSeconds(5), Duration.ofSeconds(10), () -> {
                subscriptionService.checkAndDecrementCredits(userId, finalModelId, "AI Content Generation (" + finalModelId + ")");
                return null;
            });
        }

        String voiceMode = (request != null) ? request.getVoiceMode() : null;
        if (!skipCreditDeduction) {
            aiBillingService.deductPersonalizationCredits(userId, voiceMode);
        }

        String cacheId = aiCacheService.resolveGeminiCacheId(bp, finalModelId, aiMediaService.buildVisualContext(bp) + aiMediaService.buildBrandVoiceContext(bp, null));
        String finalVisualContext = (cacheId != null) ? "[Using Cached Brand Identity]" : aiMediaService.buildVisualContext(bp);
        String finalBrandVoiceContext = (cacheId != null) ? "" : aiMediaService.buildBrandVoiceContext(bp, voiceMode);

        Map<String, String> purposeParams = aiTierService.resolvePurposeParams(request != null ? request.getContentType() : "MARKETING");
        Map<String, Object> promptParams = new HashMap<>(Map.of(
                "businessName", bp.getBusinessName() != null ? bp.getBusinessName() : "our brand",
                "niche", bp.getNiche() != null ? bp.getNiche() : "generic",
                "audience", bp.getTargetAudience() != null ? bp.getTargetAudience() : "general",
                "tone", bp.getBrandTone() != null ? bp.getBrandTone() : "professional",
                "preferredHashtags", bp.getPreferredHashtags() != null ? bp.getPreferredHashtags() : "",
                "command", aiSecurityService.guardInput(userCmd),
                "visualContext", finalVisualContext,
                "brandVoiceContext", finalBrandVoiceContext
        ));
        promptParams.putAll(purposeParams);

        AiRequest aiRequest = AiRequest.builder()
                .promptPath("content/caption")
                .templateParams(promptParams)
                .userId(userId)
                .modelId(finalModelId)
                .actionType("POST_GENERATION")
                .userCommand(userCmd)
                .cacheId(cacheId)
                .temperature((bp.getCreativityLevel() != null) ? bp.getCreativityLevel() : 0.7)
                .build();

        String traceId = org.slf4j.MDC.get("correlationId");
        logger.info("[Trace: {}] 🚀 Generating AI post [Model: {}] for user: {}", traceId, finalModelId, userId);

        double cost = AiModelSelection.fromModelId(finalModelId).getCreditCost();
        try {
            GeneratedPost generatedPost = aiOrchestrator.generateJson(aiRequest, GeneratedPost.class);
            
            if (generatedPost.getImageSuggestion() != null && !generatedPost.getImageSuggestion().isEmpty()) {
                try {
                    generatedPost.setImageUrl(aiMediaService.generateAndUploadImage(generatedPost.getImageSuggestion(), userCmd, userId, finalModelId, bp));
                } catch (Exception e) {
                    logger.error("❌ Failed to generate AI image: {}", e.getMessage());
                    if (e.getMessage() != null && e.getMessage().contains("Authentication Failed")) {
                        throw new RuntimeException("AI Content Generation failed: API Key Expired or Invalid.", e);
                    }
                }
            }
            
            return generatedPost;
        } catch (Exception e) {
            if (!skipCreditDeduction) {
                subscriptionService.refundCredits(userId, cost, "AI Generation Exception: " + e.getMessage());
            }
            throw new RuntimeException("AI Content processing failed.", e);
        }
    }

    public List<GeneratedPost> batchGeneratePosts(BusinessProfile bp, PostGenerationRequest request, Long userId) {
        String finalModelId = providerRouter.route(AiRequest.builder()
                .userId(userId)
                .modelId(request.getModelId())
                .actionType("BATCH_POST_GENERATION")
                .build()).selectedModelId();
        
        lockService.executeWithLock("credits:" + userId, Duration.ofSeconds(5), Duration.ofSeconds(10), () -> {
            subscriptionService.checkAndDecrementCredits(userId, finalModelId, request.getCount(), "AI Batch Posts Generation (" + finalModelId + ")");
            return null;
        });

        List<GeneratedPost> results = new ArrayList<>();
        for (int i = 0; i < request.getCount(); i++) {
            if (i > 0) aiMediaService.throttle(2000);
            results.add(generatePost(bp, request.getCommand(), userId, request.getModelId(), request, true));
        }
        return results;
    }

    public List<GeneratedPost> batchGenerateStories(BusinessProfile bp, PostGenerationRequest request, Long userId) {
        String finalModelId = providerRouter.route(AiRequest.builder()
                .userId(userId)
                .modelId(request.getModelId())
                .actionType("BATCH_STORY_GENERATION")
                .build()).selectedModelId();
        
        lockService.executeWithLock("credits:" + userId, Duration.ofSeconds(5), Duration.ofSeconds(10), () -> {
            subscriptionService.checkAndDecrementCredits(userId, finalModelId, request.getCount(), "AI Batch Stories Generation (" + finalModelId + ")");
            return null;
        });

        List<GeneratedPost> results = new ArrayList<>();
        for (int i = 0; i < request.getCount(); i++) {
            if (i > 0) aiMediaService.throttle(2000);
            results.add(generateStory(bp, request.getCommand(), userId, request.getModelId(), request, true));
        }
        return results;
    }

    public GeneratedPost generatePost(BusinessProfile bp, String userCmd, Long userId, String modelId, PostGenerationRequest request) {
        return generatePost(bp, userCmd, userId, modelId, request, false);
    }

    public GeneratedPost generateStory(BusinessProfile bp, String userCmd, Long userId, String modelId, PostGenerationRequest request) {
        return generateStory(bp, userCmd, userId, modelId, request, false);
    }

    public GeneratedPost generateStory(BusinessProfile bp, String userCmd, Long userId, String modelId, PostGenerationRequest request, boolean skipCreditDeduction) {
        String finalModelId = providerRouter.route(AiRequest.builder()
                .userId(userId)
                .modelId(modelId)
                .actionType("STORY_GENERATION")
                .build()).selectedModelId();
        
        if (!skipCreditDeduction) {
            lockService.executeWithLock("credits:" + userId, Duration.ofSeconds(5), Duration.ofSeconds(10), () -> {
                subscriptionService.checkAndDecrementCredits(userId, finalModelId, "AI Story Generation (" + finalModelId + ")");
                return null;
            });
        }

        if (request != null && request.getAspectRatio() != null && !request.getAspectRatio().isEmpty()) {
            try {
                bp = (BusinessProfile) bp.clone();
                bp.setAspectRatio(request.getAspectRatio());
            } catch (CloneNotSupportedException e) {
                logger.error("❌ Failed to clone profile: {}", e.getMessage());
            }
        }

        String voiceMode = (request != null) ? request.getVoiceMode() : null;
        String cacheId = aiCacheService.resolveGeminiCacheId(bp, finalModelId, aiMediaService.buildVisualContext(bp) + aiMediaService.buildBrandVoiceContext(bp, null));
        String finalVisualContext = (cacheId != null) ? "[Using Cached Brand Identity]" : aiMediaService.buildVisualContext(bp);
        String finalBrandVoiceContext = (cacheId != null) ? "" : aiMediaService.buildBrandVoiceContext(bp, voiceMode);

        Map<String, String> purposeParams = aiTierService.resolvePurposeParams(request != null ? request.getContentType() : "MARKETING");
        Map<String, Object> promptParams = new HashMap<>(Map.of(
                "businessName", bp.getBusinessName() != null ? bp.getBusinessName() : "our brand",
                "niche", bp.getNiche() != null ? bp.getNiche() : "generic",
                "audience", bp.getTargetAudience() != null ? bp.getTargetAudience() : "general audience",
                "command", aiSecurityService.guardInput(userCmd),
                "visualContext", finalVisualContext,
                "brandVoiceContext", finalBrandVoiceContext
        ));
        promptParams.putAll(purposeParams);

        AiRequest aiRequest = AiRequest.builder()
                .promptPath("content/story")
                .templateParams(promptParams)
                .userId(userId)
                .modelId(finalModelId)
                .actionType("STORY_GENERATION")
                .userCommand(userCmd)
                .cacheId(cacheId)
                .temperature((bp.getCreativityLevel() != null) ? bp.getCreativityLevel() : 0.7)
                .build();

        logger.info("📱 Generating AI story [Model: {}] for user: {}", finalModelId, userId);

        try {
            GeneratedPost generatedPost = aiOrchestrator.generateJson(aiRequest, GeneratedPost.class);
            
            if (generatedPost.getImageSuggestion() != null && !generatedPost.getImageSuggestion().isEmpty()) {
                try {
                    BusinessProfile storyBp = (BusinessProfile) bp.clone();
                    storyBp.setAspectRatio((request != null && request.getAspectRatio() != null) ? request.getAspectRatio() : "9:16");
                    generatedPost.setImageUrl(aiMediaService.generateAndUploadImage(generatedPost.getImageSuggestion(), userCmd, userId, finalModelId, storyBp));
                } catch (Exception e) {
                    logger.error("❌ Failed to generate AI story image: {}", e.getMessage());
                    if (e.getMessage() != null && e.getMessage().contains("Authentication Failed")) {
                        throw new RuntimeException("AI Story Generation failed: API Key Expired or Invalid.", e);
                    }
                }
            }
            return generatedPost;
        } catch (Exception e) {
            throw new RuntimeException("AI Story processing failed.", e);
        }
    }

    public List<String> generateThread(BusinessProfile bp, String userCmd, Long userId, String modelId) {
        String finalModelId = providerRouter.route(AiRequest.builder()
                .userId(userId)
                .modelId(modelId)
                .actionType("THREAD_GENERATION")
                .build()).selectedModelId();

        lockService.executeWithLock("credits:" + userId, Duration.ofSeconds(5), Duration.ofSeconds(10), () -> {
            subscriptionService.checkAndDecrementCredits(userId, finalModelId, "AI Thread Generation");
            return null;
        });

        String brandVoiceContext = aiMediaService.buildBrandVoiceContext(bp, null);

        AiRequest aiRequest = AiRequest.builder()
                .promptPath("content/thread")
                .templateParams(Map.of(
                        "businessName", bp.getBusinessName() != null ? bp.getBusinessName() : "our brand",
                        "niche", bp.getNiche() != null ? bp.getNiche() : "B2B/Crypto/News",
                        "tone", bp.getBrandTone() != null ? bp.getBrandTone() : "authoritative",
                        "audience", bp.getTargetAudience() != null ? bp.getTargetAudience() : "investors and professionals",
                        "command", aiSecurityService.guardInput(userCmd),
                        "brandVoiceContext", brandVoiceContext
                ))
                .userId(userId)
                .modelId(finalModelId)
                .actionType("THREAD_GENERATION")
                .userCommand(userCmd)
                .build();

        logger.info("🧵 Generating AI thread [Model: {}] for user: {}", finalModelId, userId);

        try {
            JsonNode root = aiOrchestrator.generateJsonNode(aiRequest);
            List<String> tweets = new ArrayList<>();
            JsonNode tweetsNode = root.path("tweets");
            if (tweetsNode.isArray()) {
                for (JsonNode tweet : tweetsNode) tweets.add(tweet.asText());
            }
            return tweets;
        } catch (Exception e) {
            throw new RuntimeException("AI Thread Generation failed.", e);
        }
    }

    public CarouselResponse generateCarousel(BusinessProfile bp, CarouselGenerationRequest request, Long userId, String modelId, boolean skipCreditDeduction) {
        String finalModelId = providerRouter.route(AiRequest.builder()
                .userId(userId)
                .modelId(modelId)
                .actionType("CAROUSEL_GENERATION")
                .build()).selectedModelId();

        if (request != null && request.getAspectRatio() != null && !request.getAspectRatio().isEmpty()) {
            try {
                bp = (BusinessProfile) bp.clone();
                bp.setAspectRatio(request.getAspectRatio());
            } catch (CloneNotSupportedException e) {
                logger.error("❌ Failed to clone profile: {}", e.getMessage());
            }
        }
                             
        int slideCount = request.getSlideCount() > 0 ? request.getSlideCount() : 3;

        if (!skipCreditDeduction) {
            lockService.executeWithLock("credits:" + userId, Duration.ofSeconds(5), Duration.ofSeconds(10), () -> {
                subscriptionService.checkAndDecrementCredits(userId, finalModelId, slideCount, "AI Carousel Generation (" + finalModelId + ")");
                return null;
            });
        }

        String voiceMode = (request != null) ? request.getVoiceMode() : null;
        if (!skipCreditDeduction) {
            aiBillingService.deductPersonalizationCredits(userId, voiceMode);
        }
        
        String cacheId = aiCacheService.resolveGeminiCacheId(bp, finalModelId, aiMediaService.buildVisualContext(bp) + aiMediaService.buildBrandVoiceContext(bp, null));
        String finalVisualContext = (cacheId != null) ? "[Using Cached Brand Identity]" : aiMediaService.buildVisualContext(bp);
        String finalBrandVoiceContext = (cacheId != null) ? "" : aiMediaService.buildBrandVoiceContext(bp, voiceMode);

        Map<String, String> purposeParams = aiTierService.resolvePurposeParams(request != null ? request.getContentType() : "MARKETING");
        Map<String, Object> promptParams = new HashMap<>(Map.of(
                "businessName", bp.getBusinessName() != null ? bp.getBusinessName() : "our brand",
                "niche", bp.getNiche() != null ? bp.getNiche() : "generic",
                "tone", bp.getBrandTone() != null ? bp.getBrandTone() : "professional",
                "audience", bp.getTargetAudience() != null ? bp.getTargetAudience() : "general audience",
                "command", aiSecurityService.guardInput(request.getCommand()),
                "slideCount", slideCount,
                "visualContext", finalVisualContext,
                "brandVoiceContext", finalBrandVoiceContext
        ));
        promptParams.putAll(purposeParams);

        AiRequest aiRequest = AiRequest.builder()
                .promptPath("content/carousel")
                .templateParams(promptParams)
                .userId(userId)
                .modelId(finalModelId)
                .actionType("CAROUSEL_GENERATION")
                .userCommand(request.getCommand())
                .cacheId(cacheId)
                .temperature((bp.getCreativityLevel() != null) ? bp.getCreativityLevel() : 0.7)
                .build();

        logger.info("🎠 Generating AI Carousel [Slides: {}, Model: {}] for user: {}", slideCount, finalModelId, userId);

        double totalCost = AiModelSelection.fromModelId(finalModelId).getCreditCost() * slideCount;

        try {
            CarouselResponse carouselResponse = aiOrchestrator.generateJson(aiRequest, CarouselResponse.class);
            
            if (carouselResponse.getSlides() != null) {
                for (CarouselSlide slide : carouselResponse.getSlides()) {
                    if (slide.getImageSuggestion() != null && !slide.getImageSuggestion().isEmpty()) {
                        try {
                            slide.setImageUrl(aiMediaService.generateAndUploadImage(slide.getImageSuggestion(), request.getCommand(), userId, finalModelId, bp));
                        } catch (Exception e) {
                            logger.error("❌ Failed to generate slide image: {}", e.getMessage());
                            if (e.getMessage() != null && e.getMessage().contains("Authentication Failed")) {
                                throw new RuntimeException("AI Carousel Generation failed: API Key Expired or Invalid.", e);
                            }
                        }
                    }
                }
            }
            
            return carouselResponse;
        } catch (Exception e) {
            if (!skipCreditDeduction) subscriptionService.refundCredits(userId, totalCost, "Carousel Generation Error");
            throw new RuntimeException("AI Carousel processing failed.", e);
        }
    }

    public CarouselResponse generateCarousel(BusinessProfile bp, CarouselGenerationRequest request, Long userId, String modelId) {
        return generateCarousel(bp, request, userId, modelId, false);
    }

    public ReelResponse generateReel(BusinessProfile bp, String userCmd, Long userId, String modelId, PostGenerationRequest request) {
        String finalModelId = providerRouter.route(AiRequest.builder()
                .userId(userId)
                .modelId(modelId)
                .actionType("REEL_GENERATION")
                .build()).selectedModelId();
        
        lockService.executeWithLock("credits:" + userId, Duration.ofSeconds(5), Duration.ofSeconds(10), () -> {
            subscriptionService.checkAndDecrementCredits(userId, finalModelId, "AI Reel Generation (" + finalModelId + ")");
            return null;
        });

        if (request != null && request.getAspectRatio() != null && !request.getAspectRatio().isEmpty()) {
            try {
                bp = (BusinessProfile) bp.clone();
                bp.setAspectRatio(request.getAspectRatio());
            } catch (CloneNotSupportedException e) {
                logger.error("❌ Failed to clone profile: {}", e.getMessage());
            }
        }

        String voiceMode = (request != null) ? request.getVoiceMode() : null;
        aiBillingService.deductPersonalizationCredits(userId, voiceMode);
        
        Map<String, String> purposeParams = aiTierService.resolvePurposeParams(request != null ? request.getContentType() : "MARKETING");
        Map<String, Object> promptParams = new HashMap<>(Map.of(
                "businessName", bp.getBusinessName() != null ? bp.getBusinessName() : "our brand",
                "niche", bp.getNiche() != null ? bp.getNiche() : "generic",
                "audience", bp.getTargetAudience() != null ? bp.getTargetAudience() : "general audience",
                "tone", bp.getBrandTone() != null ? bp.getBrandTone() : "energetic",
                "command", aiSecurityService.guardInput(userCmd),
                "visualContext", aiMediaService.buildVisualContext(bp),
                "brandVoiceContext", aiMediaService.buildBrandVoiceContext(bp, voiceMode),
                "preferredHashtags", bp.getPreferredHashtags() != null ? bp.getPreferredHashtags() : ""
        ));
        promptParams.putAll(purposeParams);

        AiRequest aiRequest = AiRequest.builder()
                .promptPath("content/reel")
                .templateParams(promptParams)
                .userId(userId)
                .modelId(finalModelId)
                .actionType("REEL_GENERATION")
                .userCommand(userCmd)
                .temperature((bp.getCreativityLevel() != null) ? bp.getCreativityLevel() : 0.7)
                .build();

        logger.info("🎬 Generating AI Reel [Model: {}] for user: {}", finalModelId, userId);

        try {
            ReelResponse reelResponse = aiOrchestrator.generateJson(aiRequest, ReelResponse.class);
            
            if (reelResponse.getImageSuggestion() != null && !reelResponse.getImageSuggestion().isEmpty()) {
                try {
                    BusinessProfile reelBp = (BusinessProfile) bp.clone();
                    reelBp.setAspectRatio((request != null && request.getAspectRatio() != null) ? request.getAspectRatio() : "9:16");
                    reelResponse.setImageUrl(aiMediaService.generateAndUploadImage(reelResponse.getImageSuggestion(), userCmd, userId, finalModelId, reelBp));
                } catch (Exception e) {
                    logger.error("❌ Failed to generate AI reel image: {}", e.getMessage());
                    if (e.getMessage() != null && e.getMessage().contains("Authentication Failed")) {
                        throw new RuntimeException("AI Reel Generation failed: API Key Expired or Invalid.", e);
                    }
                }
            }
            return reelResponse;
        } catch (Exception e) {
            throw new RuntimeException("AI Reel processing failed.", e);
        }
    }
}
