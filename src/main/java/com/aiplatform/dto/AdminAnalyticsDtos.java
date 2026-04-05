package com.aiplatform.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

public class AdminAnalyticsDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminDashboardStats {
        private Long totalUsers;
        private Long activeUsersToday;
        private Long activeUsersThisWeek;
        private Long totalTokensUsed;
        private Double totalCreditsSpent;
        private Map<String, Long> userGrowth; // Date -> Count
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelUsageStats {
        private String modelId;
        private Long totalTokens;
        private Long promptTokens;
        private Long completionTokens;
        private Long callCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserIntelligenceDto {
        private Long userId;
        private String email;
        private String fullName;
        private Long totalTokens;
        private Long loginCount;
        private String lastLoginAt;
        private String topModelId;
        private Map<String, Long> modelBreakdown; // Model -> Tokens
    }
}
