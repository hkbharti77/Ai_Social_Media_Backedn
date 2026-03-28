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
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GeneratedPost {
        private String caption;
        private List<String> hashtags;
        private String imageUrl;
        private String imageSuggestion;
    }

    @Data
    @AllArgsConstructor
    public static class GenerationResponse {
        private List<GeneratedPost> posts;
    }
}
