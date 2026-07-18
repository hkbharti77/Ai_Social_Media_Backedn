package com.aiplatform.dto;

import com.aiplatform.model.PostStatus;
import com.aiplatform.model.SubscriptionTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AdminManagementDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserActionResponse {
        private Long id;
        private String email;
        private Boolean isActive;
        private SubscriptionTier subscriptionTier;
        private LocalDateTime subscriptionExpiresAt;
        private Double monthlyCredits;
        private Double bonusCredits;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminUserProfileResponse {
        private Long id;
        private String email;
        private String fullName;
        private Boolean isActive;
        private SubscriptionTier subscriptionTier;
        private LocalDateTime subscriptionExpiresAt;
        private Double monthlyCredits;
        private Double bonusCredits;
        private Boolean isFraudFlagged;
        private String registrationIp;
        private String deviceFingerprint;
        private Long loginCount;
        private LocalDateTime lastLoginAt;
        private LocalDateTime createdAt;
        private Set<String> roles;
        private Integer storedImagesCount;
        private Integer storedVideosCount;
        private Long totalUsageMinutes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FraudFlaggedUserDto {
        private Long id;
        private String email;
        private String fullName;
        private Boolean isActive;
        private SubscriptionTier subscriptionTier;
        private String registrationIp;
        private Boolean isFraudFlagged;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FraudFlagResponse {
        private Long id;
        private String email;
        private Boolean isFraudFlagged;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreditAdjustmentRequest {
        @NotNull
        private Long userId;
        
        @Positive
        private Double amount;
        
        @NotBlank
        private String creditType;   // "MONTHLY" or "BONUS"
        
        @NotBlank
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriptionChangeRequest {
        @NotNull
        private Long userId;
        
        @NotNull
        private SubscriptionTier tier;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenueStatsResponse {
        private Long totalRevenue;          // paise, all-time COMPLETED
        private Long monthlyRevenue;        // paise, current calendar month
        private Long dailyRevenue;          // paise, current calendar day
        private Long totalCompletedCount;
        private Long monthlyCompletedCount;
        private Long dailyCompletedCount;
        private String currency;            // "INR"
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreditStatsResponse {
        private Double totalCredits;        // all-time sum
        private Double monthlyCredits;      // current calendar month
        private List<TopCreditUser> topUsers;
        private Map<String, Double> byPurpose;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopCreditUser {
        private Long userId;
        private String email;
        private Double totalCredits;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminPostDto {
        private Long id;
        private Long userId;
        private String userEmail;
        private String caption;             // truncated to 200 chars
        private PostStatus status;
        private String platform;
        private LocalDateTime createdAt;
        private LocalDateTime publishedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostStatsResponse {
        private Long total;
        private Map<String, Long> byStatus; // "DRAFT" -> count, etc.
        private Long thisMonth;
        private Long publishedToday;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemStatsResponse {
        private Long totalUsers;
        private Map<String, Long> usersByTier;
        private Long activeUsers;
        private Long fraudFlaggedUsers;
        private Long totalRevenue;          // paise
        private Double totalCreditsConsumed;
        private Long totalPosts;
        private Long totalAiTokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BroadcastEmailRequest {
        @NotBlank
        private String subject;
        
        @NotBlank
        private String htmlBody;
        
        private String targetTier;  // "ALL" | SubscriptionTier name | "SPECIFIC"
        
        private String targetEmail; // Used if targetTier is "SPECIFIC"
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BroadcastEmailResponse {
        private Integer recipientCount;
        private String status;              // "DISPATCHED"
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuspiciousRegistrationsResponse {
        private List<DuplicateIpGroup> duplicateIpGroups;
        private List<DuplicateFingerprintGroup> duplicateFingerprintGroups;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DuplicateIpGroup {
        private String registrationIp;
        private List<UserSummary> users;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DuplicateFingerprintGroup {
        private String deviceFingerprint;
        private List<UserSummary> users;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSummary {
        private Long userId;
        private String email;
    }
}
