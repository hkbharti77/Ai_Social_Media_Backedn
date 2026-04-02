package com.aiplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

public class ContentGenerationDtos {

    @Data
    public static class PostGenerationRequest {
        private String command;
        private int count = 1;
        private String modelId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GeneratedPost {
        private String caption;
        private List<String> hashtags;
        private String imageUrl;
        private String videoUrl;
        private String imageSuggestion;
    }

    @Data
    @AllArgsConstructor
    public static class GenerationResponse {
        private List<GeneratedPost> posts;
    }
    @Data
    public static class ContentGapRequest {
        private String businessType;
        private String city;
        private String targetAudience;
    }

    @Data
    public static class GapAnalysisResult {
        private String topic;
        private String whyItWorks;
        private String sampleCaption;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ContentGapResponse {
        private List<GapAnalysisResult> ideas;
    }
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PerformancePredictionRequest {
        private String draft;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MemeRequest {
        private String modelId;
        private String command;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MemeResponse {
        private String imageUrl;
        private String caption;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ViralOpportunityRequest {
        private String nicheTopic; // e.g. "AI", "Real Estate", "Crypto"
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ViralOpportunityResponse {
        private String trend;
        private String viralGap;
        private String draftPost;
        private List<String> hashtags;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CarouselGenerationRequest {
        private String command;
        private int slideCount = 3;
        private String modelId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CarouselSlide {
        private int slideNumber;
        private String slideText;
        private String imageSuggestion;
        private String imageUrl;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CarouselResponse {
        private String caption;
        private List<CarouselSlide> slides;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RepurposeRequest {
        private String url;
        private String modelId;
    }
}
