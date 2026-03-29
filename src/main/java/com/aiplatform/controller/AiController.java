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
        
        BusinessProfile bp = businessProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Business Profile not found. Please create one first."));

        List<GeneratedPost> posts = new ArrayList<>();
        for (int i = 0; i < request.getCount(); i++) {
            posts.add(aiContentService.generatePost(bp, request.getCommand(), user.getId()));
        }

        return ResponseEntity.ok(new GenerationResponse(posts));
    }

    @PostMapping("/gap-analysis")
    public ResponseEntity<ContentGapResponse> generateGapAnalysis(@RequestBody ContentGapRequest request) {
        return ResponseEntity.ok(aiContentService.generateGapAnalysis(request));
    }

    @PostMapping("/predict-performance")
    public ResponseEntity<Object> predictPerformance(@RequestBody PerformancePredictionRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        BusinessProfile bp = businessProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Business Profile not found. Please create one first."));

        return ResponseEntity.ok(aiContentService.predictPerformance(request.getDraft(), bp));
    }
}
