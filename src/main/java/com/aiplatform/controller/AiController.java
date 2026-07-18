package com.aiplatform.controller;

import com.aiplatform.dto.ContentGenerationDtos.*;
import com.aiplatform.model.BusinessProfile;
import com.aiplatform.model.User;
import com.aiplatform.repository.BusinessProfileRepository;
import com.aiplatform.service.*;
import com.aiplatform.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiContentService aiContentService;
    private final AiStrategyService aiStrategyService;
    private final AiEngagementService aiEngagementService;
    private final AiCampaignService aiCampaignService;
    private final VeoVideoService veoVideoService;
    private final SubscriptionService subscriptionService;
    private final VideoLimitService videoLimitService;
    private final VideoCreditService videoCreditService;
    private final BusinessProfileRepository businessProfileRepository;

    @GetMapping("/video-models")
    public ResponseEntity<?> getAvailableVideoModels() {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        String userTier = toDisplayTierName(user.getSubscriptionTier());

        int remaining = videoLimitService.getRemainingFreeVideos(user.getId());
        int monthlyLimit = VideoLimitService.getMonthlyVideoLimit(userTier.toLowerCase());

        java.util.List<java.util.Map<String, Object>> models = new java.util.ArrayList<>();
        for (com.aiplatform.model.VeoModelSelection model : com.aiplatform.model.VeoModelSelection.values()) {
            java.util.Map<String, Object> modelInfo = new java.util.HashMap<>();
            modelInfo.put("modelId", model.getModelId());
            modelInfo.put("qualityTier", model.getQualityTier());
            modelInfo.put("description", model.getDescription());
            modelInfo.put("requiredLevel", model.getRequiredLevel());
            modelInfo.put("requiredTier", getRequiredTierName(model.getRequiredLevel()));
            modelInfo.put("accessible", model.isAccessibleByTier(userTier));
            int extraPrice = VideoLimitService.getExtraVideoPrice(userTier.toLowerCase(), model);
            modelInfo.put("extraVideoPrice", extraPrice > 0 ? extraPrice : null);
            modelInfo.put("extraVideoPriceInr", extraPrice > 0 ? "₹" + extraPrice : "N/A");
            models.add(modelInfo);
        }

        boolean canGenerateVideo = !"free".equalsIgnoreCase(userTier) && !"creator".equalsIgnoreCase(userTier);

        return ResponseEntity.ok(java.util.Map.of(
            "models", models,
            "userTier", userTier,
            "canGenerateVideo", canGenerateVideo,
            "monthlyVideoLimit", monthlyLimit,
            "videosRemaining", remaining,
            "videosUsed", monthlyLimit - remaining
        ));
    }

    @PostMapping("/generate")
    public ResponseEntity<GenerationResponse> generatePosts(@Valid @RequestBody PostGenerationRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        BusinessProfile bp = getFirstProfile(user);

        int count = Math.min(request.getCount(), 20);
        request.setCount(count);

        List<GeneratedPost> posts = aiContentService.batchGeneratePosts(bp, request, user.getId());
        return ResponseEntity.ok(new GenerationResponse(posts));
    }

    @PostMapping("/story")
    public ResponseEntity<GenerationResponse> generateStory(@Valid @RequestBody PostGenerationRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        BusinessProfile bp = getFirstProfile(user);
        
        int count = Math.min(request.getCount(), 20);
        request.setCount(count);

        List<GeneratedPost> posts = aiContentService.batchGenerateStories(bp, request, user.getId());
        return ResponseEntity.ok(new GenerationResponse(posts));
    }

    @PostMapping("/gap-analysis")
    public ResponseEntity<GapAnalysisResponse> generateGapAnalysis(@Valid @RequestBody ContentGapRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        BusinessProfile bp = getFirstProfile(user);
        // Note: Strategy service method signature might need adjustment or Controller extracts fields
        return ResponseEntity.ok(aiStrategyService.generateGapAnalysis(
                bp.getBusinessName(), bp.getNiche(), request.getBusinessType(), request.getCity(), request.getTargetAudience(), user.getId()
        ));
    }

    @PostMapping("/predict-performance")
    public ResponseEntity<Object> predictPerformance(@Valid @RequestBody PerformancePredictionRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        BusinessProfile bp = getFirstProfile(user);
        return ResponseEntity.ok(aiStrategyService.predictPerformance(bp, request.getDraft(), user.getId()));
    }

    @GetMapping("/content-strategy")
    public ResponseEntity<ContentStrategyResponse> generateContentStrategy() {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

        BusinessProfile bp = getFirstProfile(user);
        return ResponseEntity.ok(aiStrategyService.generateContentStrategy(bp.getNiche(), "Global", bp.getTargetAudience(), user.getId()));
    }

    @PostMapping("/meme")
    public ResponseEntity<MemeResponse> generateMeme(@Valid @RequestBody MemeRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        BusinessProfile bp = getFirstProfile(user);
        return ResponseEntity.ok(aiEngagementService.generateMeme(bp, request.getModelId(), request.getCommand(), user.getId()));
    }

    @PostMapping("/viral-opportunity")
    public ResponseEntity<ViralOpportunityResponse> generateViralOpportunity(@Valid @RequestBody ViralOpportunityRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        BusinessProfile bp = getFirstProfile(user);
        return ResponseEntity.ok(aiEngagementService.generateViralOpportunity(bp, request.getNicheTopic(), user.getId()));
    }

    @PostMapping("/carousel")
    public ResponseEntity<CarouselResponse> generateCarousel(@Valid @RequestBody CarouselGenerationRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        BusinessProfile bp = getFirstProfile(user);
        return ResponseEntity.ok(aiContentService.generateCarousel(bp, request, user.getId(), request.getModelId()));
    }

    @PostMapping("/repurpose")
    public ResponseEntity<GenerationResponse> repurposeUrl(@Valid @RequestBody RepurposeRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        BusinessProfile bp = getFirstProfile(user);
        
        if (request.getAspectRatio() != null && !request.getAspectRatio().isEmpty()) {
            try {
                bp = (BusinessProfile) bp.clone();
                bp.setAspectRatio(request.getAspectRatio());
            } catch (CloneNotSupportedException e) {
                // Keep original
            }
        }

        List<GeneratedPost> generatedPosts = aiCampaignService.repurposeContent(bp, request, user.getId(), request.getModelId());
        return ResponseEntity.ok(new GenerationResponse(generatedPosts));
    }

    @PostMapping("/poll")
    public ResponseEntity<PollResponse> generatePoll(@Valid @RequestBody PostGenerationRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        BusinessProfile bp = getFirstProfile(user);
        return ResponseEntity.ok(aiEngagementService.generatePoll(bp, request.getCommand(), user.getId(), request.getModelId()));
    }

    @PostMapping("/reel")
    public ResponseEntity<?> generateReel(@Valid @RequestBody PostGenerationRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        BusinessProfile bp = getFirstProfile(user);

        if (Boolean.TRUE.equals(request.getGenerateActualVideo())) {
            String userTier = toDisplayTierName(user.getSubscriptionTier());
            String selectedModelId = request.getVideoModelId() != null ? request.getVideoModelId() : "veo-lite";
            com.aiplatform.model.VeoModelSelection veoModel = com.aiplatform.model.VeoModelSelection.fromModelId(selectedModelId);

            try {
                videoLimitService.validateAndCheckVideoAccess(user.getId(), userTier, veoModel);
            } catch (com.aiplatform.exception.InsufficientCreditsException e) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(java.util.Map.of(
                        "error", "Video generation not available on your plan",
                        "message", e.getMessage(),
                        "currentTier", userTier
                    ));
            }

            if (!videoCreditService.hasCreditForModel(user, veoModel)) {
                int cheapestPack = switch (veoModel) {
                    case VEO_LITE     -> 55;
                    case VEO_FAST     -> 140;
                    case VEO_STANDARD -> 470;
                };
                return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(java.util.Map.of(
                        "error",             "No video credits",
                        "message",           "You have no " + veoModel.getQualityTier() + " video credits. Buy a pack to continue.",
                        "modelId",           veoModel.getModelId(),
                        "cheapestPackPrice", "₹" + cheapestPack,
                        "wallet",            videoCreditService.getWalletBalance(user)
                    ));
            }

            try {
                videoCreditService.deductVideoCredit(user.getId(), veoModel);
                VideoGenerationResponse videoResponse = veoVideoService.generateVideo(bp, request, user.getId());
                java.util.Map<String, Object> wallet = videoCreditService.getWalletBalance(user);

                return ResponseEntity.ok(java.util.Map.of(
                    "video",          videoResponse,
                    "creditDeducted", "1 " + veoModel.getQualityTier() + " video credit",
                    "wallet",         wallet
                ));
            } catch (com.aiplatform.exception.VeoRateLimitException e) {
                videoCreditService.refundVideoCredit(user.getId(), veoModel);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage());
            } catch (com.aiplatform.exception.VeoTimeoutException e) {
                videoCreditService.refundVideoCredit(user.getId(), veoModel);
                return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(e.getMessage());
            } catch (Exception e) {
                videoCreditService.refundVideoCredit(user.getId(), veoModel);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Video generation failed: " + e.getMessage());
            }
        }

        return ResponseEntity.ok(aiContentService.generateReel(bp, request.getCommand(), user.getId(), request.getModelId(), request));
    }

    private String toDisplayTierName(com.aiplatform.model.SubscriptionTier tier) {
        if (tier == null) return "Free";
        return switch (tier) {
            case FREE      -> "Free";
            case CREATOR   -> "Creator";
            case STANDARD  -> "Standard";
            case PRO       -> "Pro";
            case SUPER_PRO -> "Super Pro";
        };
    }
    
    private String getRequiredTierName(int level) {
        return switch (level) {
            case 1 -> "Standard";
            case 2 -> "Pro";
            case 3 -> "Super Pro";
            default -> "Free";
        };
    }
    
    @PostMapping("/campaign")
    public ResponseEntity<CampaignResponse> generateCampaign(@Valid @RequestBody CampaignGenerationRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        BusinessProfile bp = getFirstProfile(user);
        return ResponseEntity.ok(aiCampaignService.generateCampaign(bp, request, user.getId()));
    }

    private BusinessProfile getFirstProfile(User user) {
        List<BusinessProfile> profiles = businessProfileRepository.findAllByUser(user);
        if (profiles.isEmpty()) {
            throw new RuntimeException("Business Profile not found. Please create one first.");
        }
        return profiles.get(0);
    }
}
