package com.aiplatform.service;

import com.aiplatform.dto.AiRequest;
import com.aiplatform.dto.ContentGenerationDtos.*;
import com.aiplatform.model.BusinessProfile;
import com.aiplatform.model.User;
import com.aiplatform.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiStrategyService {

    private final Logger logger = LoggerFactory.getLogger(AiStrategyService.class);

    private final AiOrchestrator aiOrchestrator;
    private final AiTierService aiTierService;
    private final AiSecurityService aiSecurityService;
    private final UserRepository userRepository;



    public GapAnalysisResponse generateGapAnalysis(String senderName, String senderNiche, String targetType, String city, String targetAudience, Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        String finalModelId = null; // Let Router decide default

        AiRequest request = AiRequest.builder()
                .promptPath("strategy/gap_analysis")
                .templateParams(Map.of(
                        "senderName", senderName,
                        "senderNiche", senderNiche,
                        "targetType", targetType,
                        "targetAudience", targetAudience,
                        "city", city
                ))
                .userId(userId)
                .modelId(finalModelId)
                .actionType("GAP_ANALYSIS")
                .userCommand(targetType + " in " + city)
                .build();

        return aiOrchestrator.generateJson(request, GapAnalysisResponse.class);
    }

    public ContentStrategyResponse generateContentStrategy(String businessType, String city, String targetAudience, Long userId) {
        String finalModelId = null; // Let Router decide default

        AiRequest request = AiRequest.builder()
                .promptPath("strategy/content_strategy")
                .templateParams(Map.of(
                        "businessType", businessType,
                        "targetAudience", targetAudience,
                        "city", city
                ))
                .userId(userId)
                .modelId(finalModelId)
                .actionType("CONTENT_STRATEGY")
                .userCommand(businessType + " in " + city)
                .build();

        return aiOrchestrator.generateJson(request, ContentStrategyResponse.class);
    }

    public PerformancePredictionResponse predictPerformance(BusinessProfile bp, String postDraft, Long userId) {
        String finalModelId = null; // Let Router decide default

        AiRequest request = AiRequest.builder()
                .promptPath("strategy/performance")
                .templateParams(Map.of(
                        "businessType", bp.getNiche() != null ? bp.getNiche() : "generic",
                        "targetAudience", bp.getTargetAudience() != null ? bp.getTargetAudience() : "general",
                        "brandTone", bp.getBrandTone() != null ? bp.getBrandTone() : "professional",
                        "postDraft", aiSecurityService.guardInput(postDraft)
                ))
                .userId(userId)
                .modelId(finalModelId)
                .actionType("PERFORMANCE_PREDICTION")
                .userCommand("Draft Analysis")
                .build();

        return aiOrchestrator.generateJson(request, PerformancePredictionResponse.class);
    }
}
