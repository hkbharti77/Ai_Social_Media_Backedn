package com.aiplatform.controller;

import com.aiplatform.dto.ContentGenerationDtos.*;
import com.aiplatform.model.BusinessProfile;
import com.aiplatform.model.User;
import com.aiplatform.repository.BusinessProfileRepository;
import com.aiplatform.service.AiContentService;
import com.aiplatform.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiContentService aiContentService;
    private final BusinessProfileRepository businessProfileRepository;

    @PostMapping("/generate")
    public ResponseEntity<GenerationResponse> generatePosts(@RequestBody PostGenerationRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        List<BusinessProfile> profiles = businessProfileRepository.findAllByUser(user);
        if (profiles.isEmpty()) {
            throw new RuntimeException("Business Profile not found. Please create one first.");
        }
        BusinessProfile bp = profiles.get(0); // Agency Mode: Using first profile by default

        List<GeneratedPost> posts = new ArrayList<>();
        for (int i = 0; i < request.getCount(); i++) {
            posts.add(aiContentService.generatePost(bp, request.getCommand(), user.getId(), request.getModelId()));
        }

        return ResponseEntity.ok(new GenerationResponse(posts));
    }

    @PostMapping("/gap-analysis")
    public ResponseEntity<ContentGapResponse> generateGapAnalysis(@RequestBody ContentGapRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        List<BusinessProfile> profiles = businessProfileRepository.findAllByUser(user);
        if (profiles.isEmpty()) {
            throw new RuntimeException("Please create your Business Profile first to run B2B Growth Strategy.");
        }
        BusinessProfile bp = profiles.get(0);

        return ResponseEntity.ok(aiContentService.generateGapAnalysis(request, bp));
    }

    @PostMapping("/predict-performance")
    public ResponseEntity<Object> predictPerformance(@RequestBody PerformancePredictionRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        List<BusinessProfile> profiles = businessProfileRepository.findAllByUser(user);
        if (profiles.isEmpty()) {
            throw new RuntimeException("Business Profile not found. Please create one first.");
        }
        BusinessProfile bp = profiles.get(0);

        return ResponseEntity.ok(aiContentService.predictPerformance(request.getDraft(), bp));
    }

    @GetMapping("/content-strategy")
    public ResponseEntity<ContentGapResponse> generateContentStrategy() {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

        List<BusinessProfile> profiles = businessProfileRepository.findAllByUser(user);
        if (profiles.isEmpty()) {
            throw new RuntimeException("Please create your Business Profile first to generate a Content Strategy.");
        }
        BusinessProfile bp = profiles.get(0);

        return ResponseEntity.ok(aiContentService.generateContentStrategy(bp));
    }
}
