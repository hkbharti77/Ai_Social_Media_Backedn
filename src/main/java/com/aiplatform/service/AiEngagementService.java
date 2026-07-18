package com.aiplatform.service;

import com.aiplatform.dto.AiRequest;
import com.aiplatform.dto.ContentGenerationDtos.*;
import com.aiplatform.model.BusinessProfile;
import com.aiplatform.model.User;
import com.aiplatform.repository.UserRepository;
import com.aiplatform.util.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class AiEngagementService {

    private final Logger logger = LoggerFactory.getLogger(AiEngagementService.class);

    private final AiOrchestrator aiOrchestrator;
    private final AiMediaService aiMediaService;
    private final AiCacheService aiCacheService;
    private final AiSecurityService aiSecurityService;
    private final AiBillingService aiBillingService;
    private final AiTierService aiTierService;
    private final ProviderRouter providerRouter;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final DistributedLockService lockService;


    public MemeResponse generateMeme(BusinessProfile bp, String modelId, String command, Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        AiRequest routerRequest = AiRequest.builder()
                .userId(userId)
                .modelId(modelId)
                .actionType("IMAGE_GENERATION")
                .build();
        String finalModelId = providerRouter.route(routerRequest).selectedModelId();
        
        String commandText = (command != null && !command.trim().isEmpty()) 
                ? "SPECIFIC INSTRUCTION / TOPIC: " + command.trim() 
                : "Make it relevant to general industry trends.";

        String cacheId = aiCacheService.resolveGeminiCacheId(bp, finalModelId, aiMediaService.buildVisualContext(bp) + aiMediaService.buildBrandVoiceContext(bp, null));
        String finalVisualContext = (cacheId != null) ? "[Using Cached Brand Identity]" : aiMediaService.buildVisualContext(bp);
        String finalBrandVoiceContext = (cacheId != null) ? "" : aiMediaService.buildBrandVoiceContext(bp, null);

        Map<String, String> purposeParams = aiTierService.resolvePurposeParams("MARKETING");
        Map<String, Object> promptParams = new HashMap<>(Map.of(
                "businessName", bp.getBusinessName() != null ? bp.getBusinessName() : "our brand",
                "niche", bp.getNiche() != null ? bp.getNiche() : "generic",
                "audience", bp.getTargetAudience() != null ? bp.getTargetAudience() : "general audience",
                "commandText", commandText,
                "tone", bp.getBrandTone() != null ? bp.getBrandTone() : "witty",
                "visualContext", finalVisualContext,
                "brandVoiceContext", finalBrandVoiceContext,
                "jsonStructure", "{\"caption\": \"...\", \"memeTextTop\": \"...\", \"memeTextBottom\": \"...\", \"imageDescription\": \"...\"}"
        ));
        promptParams.putAll(purposeParams);

        AiRequest aiRequest = AiRequest.builder()
                .promptPath("engagement/meme")
                .templateParams(promptParams)
                .userId(userId)
                .modelId(finalModelId)
                .actionType("MEME_GENERATION")
                .userCommand(command)
                .cacheId(cacheId)
                .build();

        try {
            JsonNode memeJson = aiOrchestrator.generateJsonNode(aiRequest);
            String caption = memeJson.path("caption").asText();
            String topText = memeJson.path("memeTextTop").asText();
            String bottomText = memeJson.path("memeTextBottom").asText();
            String scene = memeJson.path("imageDescription").asText();

            String memeVisualPrompt = String.format(
                    "A professional meme. SCENE: %s. " +
                    "IMPORTANT: Render the following text DIRECTLY ON THE IMAGE. " +
                    "TOP TEXT: '%s'. BOTTOM TEXT: '%s'. " +
                    "Style: IMPACT MEME FONT, BOLD WHITE WITH BLACK OUTLINE.",
                    scene, topText, bottomText);

            String imageUrl = aiMediaService.generateAndUploadImage(memeVisualPrompt, "Generate a meme image", userId, finalModelId, bp);
            
            return new MemeResponse(imageUrl, caption);
        } catch (Exception e) {
            logger.error("❌ Failed to process meme generation: {}", e.getMessage());
            subscriptionService.refundCredits(userId, 1.0, "Meme Generation Failure");
            throw new RuntimeException("Meme processing failed.");
        }
    }

    public PollResponse generatePoll(BusinessProfile bp, String userCmd, Long userId, String modelId) {
        String finalModelId = "gemini-2.5-flash-lite";
        
        lockService.executeWithLock("credits:" + userId, Duration.ofSeconds(5), Duration.ofSeconds(10), () -> {
            subscriptionService.checkAndDecrementCredits(userId, finalModelId, "AI Poll Generation");
            return null;
        });

        Map<String, String> purposeParams = aiTierService.resolvePurposeParams("MARKETING");
        Map<String, Object> promptParams = new HashMap<>(Map.of(
                "businessName", bp.getBusinessName() != null ? bp.getBusinessName() : "our brand",
                "niche", bp.getNiche() != null ? bp.getNiche() : "generic",
                "audience", bp.getTargetAudience() != null ? bp.getTargetAudience() : "general audience",
                "tone", bp.getBrandTone() != null ? bp.getBrandTone() : "professional",
                "command", aiSecurityService.guardInput(userCmd),
                "jsonStructure", "{\"caption\": \"Question for the poll...\", \"options\": [\"Option 1\", \"Option 2\"], \"hashtags\": [\"#Poll\"], \"imageSuggestion\": \"Describe a visual competition between Option A and Option B...\"}"
        ));
        promptParams.putAll(purposeParams);

        AiRequest aiRequest = AiRequest.builder()
                .promptPath("engagement/poll")
                .templateParams(promptParams)
                .userId(userId)
                .modelId(finalModelId)
                .actionType("POLL_GENERATION")
                .userCommand(userCmd)
                .build();

        logger.info("📊 Generating AI poll [Model: {}] for user: {}", finalModelId, userId);

        try {
            PollResponse pollResponse = aiOrchestrator.generateJson(aiRequest, PollResponse.class);
            pollResponse.setDurationMinutes(1440); // 24 Hours default

            if (pollResponse.getImageSuggestion() != null && !pollResponse.getImageSuggestion().isEmpty()) {
                try {
                    String imageModelId = (modelId != null && !modelId.isEmpty()) ? modelId : 
                                         SecurityUtils.getCurrentUser().get().getSubscriptionTier().getDefaultImageModel();
                    
                    lockService.executeWithLock("credits:" + userId, Duration.ofSeconds(5), Duration.ofSeconds(10), () -> {
                        subscriptionService.checkAndDecrementCredits(userId, imageModelId, "Visual Poll Image (" + imageModelId + ")");
                        return null;
                    });

                    String imageUrl = aiMediaService.generateAndUploadImage(pollResponse.getImageSuggestion(), userCmd, userId, imageModelId, bp);
                    pollResponse.setImageUrl(imageUrl);
                } catch (Exception e) {
                    logger.error("❌ Failed to generate AI poll image: {}", e.getMessage());
                }
            }
            
            return pollResponse;
        } catch (Exception e) {
            logger.error("❌ Failed to parse or process Poll", e);
            throw new RuntimeException("AI Poll processing failed.");
        }
    }

    public String generateReviewReply(String businessName, String reviewText, int rating, Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        String finalModelId = null; // Let Router decide default
        
        AiRequest aiRequest = AiRequest.builder()
                .promptPath("engagement/review_reply")
                .templateParams(Map.of(
                        "businessName", businessName != null ? businessName : "our business",
                        "rating", rating,
                        "reviewText", reviewText
                ))
                .userId(userId)
                .modelId(finalModelId)
                .actionType("REVIEW_REPLY")
                .userCommand(reviewText.length() > 100 ? reviewText.substring(0, 100) + "..." : reviewText)
                .build();

        try {
            return aiOrchestrator.generateText(aiRequest);
        } catch (Exception e) {
            logger.error("AI Review Reply failed: {}", e.getMessage());
            return "Thank you for your feedback! We appreciate your support.";
        }
    }

    public String generateCommunityProReply(BusinessProfile bp, String commentText, String postContext, String threadContext) {
        AiRequest aiRequest = AiRequest.builder()
                .promptPath("engagement/community_reply")
                .templateParams(Map.of(
                        "businessName", bp.getBusinessName() != null ? bp.getBusinessName() : "our brand",
                        "niche", bp.getNiche() != null ? bp.getNiche() : "generic",
                        "tone", bp.getBrandTone() != null ? bp.getBrandTone() : "authentic",
                        "audience", bp.getTargetAudience() != null ? bp.getTargetAudience() : "general",
                        "postContext", postContext != null ? postContext : "N/A",
                        "threadContext", threadContext != null ? threadContext : "N/A",
                        "commentText", commentText,
                        "brandVoiceContext", aiMediaService.buildBrandVoiceContext(bp, null)
                ))
                .userId(1L) // Assuming default/system user if not passed, but ideally should be passed
                .modelId("gemini-1.5-flash")
                .actionType("COMMUNITY_REPLY")
                .build();

        try {
            return aiOrchestrator.generateText(aiRequest);
        } catch (Exception e) {
            logger.error("❌ Pro Community Reply generation failed: {}", e.getMessage());
            throw new RuntimeException("AI Reply generation failed.");
        }
    }

    public JsonNode analyzeCommunitySentiment(BusinessProfile bp, String commentText) {
        AiRequest aiRequest = AiRequest.builder()
                .promptPath("engagement/sentiment")
                .templateParams(Map.of(
                        "businessName", bp.getBusinessName() != null ? bp.getBusinessName() : "our brand",
                        "commentText", aiSecurityService.guardInput(commentText)
                ))
                .userId(1L) // Should ideally be passed
                .modelId("gemini-1.5-flash")
                .actionType("SENTIMENT_ANALYSIS")
                .build();

        try {
            return aiOrchestrator.generateJsonNode(aiRequest);
        } catch (Exception e) {
            logger.error("❌ Sentiment Analysis failed: {}", e.getMessage());
            // Need objectMapper here just to create default node if it completely fails
            ObjectMapper fallbackMapper = new ObjectMapper();
            return fallbackMapper.createObjectNode()
                    .put("sentiment", "NEUTRAL")
                    .put("priority", "MEDIUM")
                    .put("reason", "Analysis failed, defaulting.");
        }
    }

    public String analyzeBrandVoice(List<String> samples, Long userId) {
        lockService.executeWithLock("credits:" + userId, Duration.ofSeconds(5), Duration.ofSeconds(10), () -> {
            subscriptionService.deductFixedCredits(userId, 5.0, "AI Brand Voice DNA Analysis");
            return null;
        });

        String samplesText = String.join("\n---\n", samples);
        String promptText = """
                Analyze the following social media post samples and create a "Style DNA" summary.
                Focus on:
                1. Tone (e.g., Sarcastic, Professional, Hype)
                2. Vocabulary (common words, slang, sentence structure)
                3. Formatting (emoji usage, line breaks, length)
                
                Samples:
                %s
                
                Return a concise 2-3 sentence summary that an AI can use as a "persona instruction" to mimic this user. 
                Return ONLY the summary text.
                """.formatted(samplesText);

        AiRequest aiRequest = AiRequest.builder()
                .template(promptText)
                .templateParams(Map.of())
                .userId(userId)
                .modelId("gemini-1.5-flash")
                .actionType("BRAND_VOICE_ANALYSIS")
                .userCommand("Samples analyzed: " + samples.size())
                .build();

        try {
            return aiOrchestrator.generateText(aiRequest);
        } catch (Exception e) {
            logger.error("❌ Brand Voice Analysis failed: {}", e.getMessage());
            return "Professional and engaging social media style.";
        }
    }

    public ViralOpportunityResponse generateViralOpportunity(BusinessProfile bp, String topic, Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        String finalModelId = null; // Let Router decide default
        
        AiRequest aiRequest = AiRequest.builder()
                .promptPath("engagement/viral")
                .templateParams(Map.of(
                        "niche", bp.getNiche() != null ? bp.getNiche() : "generic",
                        "topic", (topic != null && !topic.isEmpty()) ? topic : "latest industry news",
                        "jsonStructure", "{\"trend\": \"...\", \"viralGap\": \"...\", \"draftPost\": \"...\", \"hashtags\": [\"#...\", \"...\"]}"
                ))
                .userId(userId)
                .modelId(finalModelId)
                .actionType("VIRAL_OPPORTUNITY")
                .userCommand(topic)
                .build();

        try {
            return aiOrchestrator.generateJson(aiRequest, ViralOpportunityResponse.class);
        } catch (Exception e) {
            logger.error("❌ Failed to parse viral opportunity: {}", e.getMessage());
            throw new RuntimeException("Viral Opportunity processing failed.");
        }
    }
}
