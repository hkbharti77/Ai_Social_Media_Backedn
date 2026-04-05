package com.aiplatform.controller;

import com.aiplatform.dto.ContentGenerationDtos.*;
import com.aiplatform.model.BusinessProfile;
import com.aiplatform.model.User;
import com.aiplatform.repository.BusinessProfileRepository;
import com.aiplatform.service.AiContentService;
import com.aiplatform.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiContentService aiContentService;
    private final BusinessProfileRepository businessProfileRepository;

    @PostMapping("/generate")
    public ResponseEntity<GenerationResponse> generatePosts(@Valid @RequestBody PostGenerationRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        BusinessProfile bp = getFirstProfile(user);

        // Security Cap: Ensure count is within limits (redundant with @Valid but safe)
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
    public ResponseEntity<ContentGapResponse> generateGapAnalysis(@Valid @RequestBody ContentGapRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        BusinessProfile bp = getFirstProfile(user);
        return ResponseEntity.ok(aiContentService.generateGapAnalysis(request, bp, user.getId()));
    }

    @PostMapping("/predict-performance")
    public ResponseEntity<Object> predictPerformance(@Valid @RequestBody PerformancePredictionRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        BusinessProfile bp = getFirstProfile(user);
        return ResponseEntity.ok(aiContentService.predictPerformance(request.getDraft(), bp, user.getId()));
    }

    @GetMapping("/content-strategy")
    public ResponseEntity<ContentGapResponse> generateContentStrategy() {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

        BusinessProfile bp = getFirstProfile(user);
        return ResponseEntity.ok(aiContentService.generateContentStrategy(bp, user.getId()));
    }

    @PostMapping("/meme")
    public ResponseEntity<MemeResponse> generateMeme(@Valid @RequestBody MemeRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        BusinessProfile bp = getFirstProfile(user);
        return ResponseEntity.ok(aiContentService.generateMeme(bp, request.getModelId(), request.getCommand(), user.getId()));
    }

    @PostMapping("/viral-opportunity")
    public ResponseEntity<ViralOpportunityResponse> generateViralOpportunity(@Valid @RequestBody ViralOpportunityRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        BusinessProfile bp = getFirstProfile(user);
        return ResponseEntity.ok(aiContentService.generateViralOpportunity(bp, request.getNicheTopic(), user.getId()));
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
        
        // Handle Aspect Ratio Override
        if (request.getAspectRatio() != null && !request.getAspectRatio().isEmpty()) {
            try {
                bp = (BusinessProfile) bp.clone();
                bp.setAspectRatio(request.getAspectRatio());
            } catch (CloneNotSupportedException e) {
                // Keep original
            }
        }

        List<GeneratedPost> generatedPosts = aiContentService.repurposeContent(bp, request, user.getId(), request.getModelId());
        return ResponseEntity.ok(new GenerationResponse(generatedPosts));
    }

    @PostMapping("/poll")
    public ResponseEntity<PollResponse> generatePoll(@Valid @RequestBody PostGenerationRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        BusinessProfile bp = getFirstProfile(user);
        return ResponseEntity.ok(aiContentService.generatePoll(bp, request.getCommand(), user.getId(), request.getModelId()));
    }

    @PostMapping("/reel")
    public ResponseEntity<ReelResponse> generateReel(@Valid @RequestBody PostGenerationRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        BusinessProfile bp = getFirstProfile(user);
        return ResponseEntity.ok(aiContentService.generateReel(bp, request.getCommand(), user.getId(), request.getModelId(), request));
    }
    
    @PostMapping("/campaign")
    public ResponseEntity<CampaignResponse> generateCampaign(@Valid @RequestBody CampaignGenerationRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        BusinessProfile bp = getFirstProfile(user);
        return ResponseEntity.ok(aiContentService.generateCampaign(bp, request, user.getId()));
    }

    private BusinessProfile getFirstProfile(User user) {
        List<BusinessProfile> profiles = businessProfileRepository.findAllByUser(user);
        if (profiles.isEmpty()) {
            throw new RuntimeException("Business Profile not found. Please create one first.");
        }
        return profiles.get(0);
    }
}

