package com.aiplatform.service;

import com.aiplatform.dto.AiRequest;
import com.aiplatform.dto.ContentGenerationDtos.*;
import com.aiplatform.model.BusinessProfile;
import com.aiplatform.model.User;
import com.aiplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiCampaignService {

    private final Logger logger = LoggerFactory.getLogger(AiCampaignService.class);

    private final AiOrchestrator aiOrchestrator;
    private final AiMediaService aiMediaService;
    private final AiCacheService aiCacheService;
    private final AiBillingService aiBillingService;
    private final AiTierService aiTierService;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final DistributedLockService lockService;



    public CampaignResponse generateCampaign(BusinessProfile bp, CampaignGenerationRequest request, Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        String finalModelId = request != null ? request.getModelId() : null;

        double totalCost;
        switch (finalModelId) {
            case "imagen-4-fast": totalCost = 30.0; break;
            case "imagen-4-standard": totalCost = 50.0; break;
            case "imagen-4-ultra": totalCost = 60.0; break;
            case "gemini-3.1-flash-image": totalCost = 40.0; break;
            case "gemini-3-pro-image": totalCost = 70.0; break;
            case "gemini-2.5-flash-image":
            default: totalCost = 20.0; break;
        }

        lockService.executeWithLock("credits:" + userId, Duration.ofSeconds(5), Duration.ofSeconds(10), () -> {
            subscriptionService.deductFixedCredits(userId, totalCost, "AI Campaign Genius (" + finalModelId + ")");
            aiBillingService.deductPersonalizationCredits(userId, request.getVoiceMode());
            return null;
        });

        if (request.getAspectRatio() != null && !request.getAspectRatio().isEmpty()) {
            try {
                bp = (BusinessProfile) bp.clone();
                bp.setAspectRatio(request.getAspectRatio());
            } catch (CloneNotSupportedException e) {
                logger.error("❌ Failed to clone profile: {}", e.getMessage());
            }
        }

        String visualContext = aiMediaService.buildVisualContext(bp);
        
        String cacheId = aiCacheService.resolveGeminiCacheId(bp, finalModelId, aiMediaService.buildVisualContext(bp) + aiMediaService.buildBrandVoiceContext(bp, null));
        String finalVisualContext = (cacheId != null) ? "[Using Cached Visual Identity]" : visualContext;

        Map<String, String> purposeParams = aiTierService.resolvePurposeParams(request.getContentType());
        Map<String, Object> promptParams = new HashMap<>(Map.of(
                "businessName", bp.getBusinessName() != null ? bp.getBusinessName() : "our brand",
                "niche", bp.getNiche() != null ? bp.getNiche() : "generic",
                "audience", bp.getTargetAudience() != null ? bp.getTargetAudience() : "general audience",
                "goal", request.getGoal(),
                "tone", bp.getBrandTone() != null ? bp.getBrandTone() : "professional",
                "visualContext", finalVisualContext
        ));
        promptParams.putAll(purposeParams);

        AiRequest aiRequest = AiRequest.builder()
                .promptPath("campaign/campaign")
                .templateParams(promptParams)
                .userId(userId)
                .modelId(finalModelId)
                .actionType("CAMPAIGN_GENERATION")
                .userCommand(request.getGoal())
                .cacheId(cacheId)
                .temperature((bp.getCreativityLevel() != null) ? bp.getCreativityLevel() : 0.8)
                .build();

        logger.info("⚔️ Generating Campaign Genius [Model: {}] for goal: {}", finalModelId, request.getGoal());

        try {
            CampaignResponse campaign = aiOrchestrator.generateJson(aiRequest, CampaignResponse.class);
            final BusinessProfile campaignBp = bp;
            String theme = campaign.getVisualTheme();

                if (campaign.getPosts() != null) {
                    for (GeneratedPost post : campaign.getPosts()) {
                        aiMediaService.throttle(2000);
                        String visualPrompt = theme + ". SCENE: " + post.getImageSuggestion();
                        try {
                            post.setImageUrl(aiMediaService.generateAndUploadImage(visualPrompt, request.getGoal(), userId, finalModelId, campaignBp));
                        } catch (Exception e) {
                            logger.error("❌ Campaign post image failed: {}", e.getMessage());
                            if (e.getMessage().contains("429")) break;
                        }
                    }
                }

                if (campaign.getStories() != null) {
                    for (GeneratedPost story : campaign.getStories()) {
                        aiMediaService.throttle(2000);
                        String visualPrompt = theme + ". VERTICAL SCENE: " + story.getImageSuggestion();
                        try {
                            BusinessProfile storyBp = (BusinessProfile) campaignBp.clone();
                            storyBp.setAspectRatio("9:16");
                            story.setImageUrl(aiMediaService.generateAndUploadImage(visualPrompt, request.getGoal(), userId, finalModelId, storyBp));
                        } catch (Exception e) {
                            logger.error("❌ Campaign story image failed: {}", e.getMessage());
                            if (e.getMessage().contains("429")) break;
                        }
                    }
                }

                if (campaign.getReel() != null) {
                    aiMediaService.throttle(2000);
                    String visualPrompt = theme + ". VERTICAL REEL THUMBNAIL: " + campaign.getReel().getImageSuggestion();
                    try {
                        BusinessProfile reelBp = (BusinessProfile) campaignBp.clone();
                        reelBp.setAspectRatio("9:16");
                        campaign.getReel().setImageUrl(aiMediaService.generateAndUploadImage(visualPrompt, request.getGoal(), userId, finalModelId, reelBp));
                    } catch (Exception e) {
                        logger.error("❌ Campaign reel image failed: {}", e.getMessage());
                    }
                }

                return campaign;
        } catch (Exception e) {
            logger.warn("🔄 Refunding Campaign credits: {} [User: {}]", totalCost, userId);
            subscriptionService.refundCredits(userId, totalCost, "Campaign Generation Failed: " + e.getMessage());
            throw e;
        }
    }

    public List<GeneratedPost> repurposeContent(BusinessProfile bp, RepurposeRequest request, Long userId, String modelId) {
        User u = userRepository.findById(userId).orElseThrow();
        String finalModelId = (modelId != null && !modelId.isEmpty()) ? modelId : u.getSubscriptionTier().getDefaultImageModel();

        if (request != null && request.getAspectRatio() != null && !request.getAspectRatio().isEmpty()) {
            try {
                bp = (BusinessProfile) bp.clone();
                bp.setAspectRatio(request.getAspectRatio());
            } catch (CloneNotSupportedException e) {
                logger.error("❌ Failed to clone profile for repurpose: {}", e.getMessage());
            }
        }

        int count = (request.getCount() > 0) ? request.getCount() : 5;
        lockService.executeWithLock("credits:" + userId, Duration.ofSeconds(5), Duration.ofSeconds(10), () -> {
            for (int i = 0; i < count; i++) {
                subscriptionService.checkAndDecrementCredits(userId, finalModelId, "AI Repurpose Content (" + finalModelId + ")");
            }
            return null;
        });

        String scrapedContent;
        try {
            Document doc = Jsoup.connect(request.getUrl())
                    .timeout(10000)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .get();
            
            StringBuilder contentBuilder = new StringBuilder();
            contentBuilder.append("Title: ").append(doc.title()).append("\n");
            Element metaDescription = doc.selectFirst("meta[name=description]");
            if (metaDescription != null) contentBuilder.append("Description: ").append(metaDescription.attr("content")).append("\n");
            
            String text = doc.body().text();
            int maxLength = 4000;
            if (text.length() > maxLength) text = text.substring(0, maxLength) + "...";
            contentBuilder.append("Content: ").append(text);
            scrapedContent = contentBuilder.toString();
        } catch (Exception e) {
            logger.error("❌ Scrape failed for URL {}: {}", request.getUrl(), e.getMessage());
            throw new RuntimeException("Failed to extract content from the provided URL.");
        }

        String cacheId = aiCacheService.resolveRepurposeCacheId(bp, scrapedContent, finalModelId);
        String finalScrapedContent = (cacheId != null) ? "[Using Cached Scraped Content]" : scrapedContent;
        String visualContext = (cacheId != null) ? "" : aiMediaService.buildVisualContext(bp);

        AiRequest aiRequest = AiRequest.builder()
                .promptPath("campaign/repurpose")
                .templateParams(Map.of(
                        "businessName", bp.getBusinessName() != null ? bp.getBusinessName() : "our brand",
                        "niche", bp.getNiche() != null ? bp.getNiche() : "generic",
                        "audience", bp.getTargetAudience() != null ? bp.getTargetAudience() : "general audience",
                        "scrapedContent", finalScrapedContent,
                        "count", request.getCount(),
                        "visualContext", visualContext
                ))
                .userId(userId)
                .modelId(finalModelId)
                .actionType("REPURPOSE_CONTENT")
                .userCommand(request.getUrl())
                .cacheId(cacheId)
                .temperature((bp.getCreativityLevel() != null) ? bp.getCreativityLevel() : 0.7)
                .build();

        try {
            GenerationResponse response = aiOrchestrator.generateJson(aiRequest, GenerationResponse.class);
            List<GeneratedPost> generatedPosts = response.getPosts();
            
            if (generatedPosts != null) {
                for (GeneratedPost post : generatedPosts) {
                    if (post.getImageSuggestion() != null && !post.getImageSuggestion().isEmpty()) {
                        aiMediaService.throttle(2000);
                        try {
                            post.setImageUrl(aiMediaService.generateAndUploadImage(post.getImageSuggestion(), "Repurpose " + request.getUrl(), userId, finalModelId, bp));
                        } catch (Exception e) {
                            logger.error("❌ Failed to generate AI image for repurposed post: {}", e.getMessage());
                        }
                    }
                }
            }
            return generatedPosts;
        } catch (Exception e) {
            logger.error("❌ AI Repurpose Generation failed: {}", e.getMessage());
            throw new RuntimeException("AI Repurpose Generation failed.");
        }
    }
}
