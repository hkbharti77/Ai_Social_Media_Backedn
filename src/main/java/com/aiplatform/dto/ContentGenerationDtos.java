package com.aiplatform.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

public class ContentGenerationDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostGenerationRequest {
        @NotBlank(message = "Command cannot be blank")
        @Size(max = 5000, message = "Command too long")
        private String command;

        @Min(value = 1, message = "Count must be at least 1")
        @Max(value = 20, message = "Count cannot exceed 20")
        private int count = 1;

        private String modelId;              // For image generation (e.g., "gemini-2.5-flash-image")
        private String videoModelId;         // For video generation (e.g., "veo-lite", "veo-fast", "veo-standard")
        private String aspectRatio;
        private String voiceMode;
        private String contentType;          // MARKETING or EDUCATIONAL
        private Boolean generateActualVideo; // true = generate video, false/null = script only
        private Boolean confirmExtraCharge;  // true = user confirmed extra pay-per-video charge
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
        private String videoScript;
        private String audioSuggestion;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GenerationResponse {
        private List<GeneratedPost> posts;
    }
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentGapRequest {
        @NotBlank(message = "Business type is required")
        private String businessType;

        @NotBlank(message = "City is required")
        private String city;

        @NotBlank(message = "Target audience is required")
        private String targetAudience;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
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
        @NotBlank(message = "Draft is required")
        @Size(max = 10000, message = "Draft too long")
        private String draft;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MemeRequest {
        private String modelId;
        @NotBlank(message = "Command is required")
        @Size(max = 2000)
        private String command;
        private String voiceMode;
        private String contentType; // MARKETING or EDUCATIONAL
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
        @NotBlank(message = "Niche topic is required")
        @Size(max = 500)
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
        @NotBlank(message = "Command is required")
        @Size(max = 5000)
        private String command;

        @Min(1)
        @Max(10)
        private int slideCount = 3;

        private String modelId;
        private String aspectRatio;
        private String voiceMode;
        private String contentType; // MARKETING or EDUCATIONAL
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
    @lombok.Builder
    public static class RepurposeRequest {
        @NotBlank(message = "URL is required")
        @Pattern(regexp = "^https?://.*", message = "Must be a valid URL")
        private String url;

        private String modelId;

        @Min(1)
        @Max(10)
        private int count = 5;

        private String aspectRatio;
        private String contentType; // MARKETING or EDUCATIONAL
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ReelResponse {
        private String caption;
        private List<String> hashtags;
        private String videoScript;
        private String audioSuggestion;
        private String imageSuggestion; // Thumbnail/Storyboard
        private String imageUrl;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @lombok.Builder
    public static class VideoGenerationResponse {
        private String videoUrl;
        private String caption;
        private List<String> hashtags;
        private String videoScript;
        private String imageUrl;
        private String audioSuggestion;
        private String generationMode;  // VEO_ACTUAL or SCRIPT_ONLY
        private String modelUsed;       // Which model was used (e.g., "veo-lite", "veo-fast")
        private Double creditsUsed;     // How many credits were deducted
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PollResponse {
        private String caption;
        private List<String> options;
        private List<String> hashtags;
        private int durationMinutes;
        private String imageUrl;
        private String imageSuggestion;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CampaignGenerationRequest {
        @NotBlank(message = "Goal is required")
        @Size(max = 2000)
        private String goal;

        private String modelId;
        private String aspectRatio;
        private String voiceMode;
        private String contentType; // MARKETING or EDUCATIONAL
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @lombok.Builder
    public static class CampaignResponse {
        private String strategySummary;
        private String visualTheme;
        private List<GeneratedPost> posts;
        private List<GeneratedPost> stories;
        private ReelResponse reel;
        private List<String> hashtags;
    }
}

