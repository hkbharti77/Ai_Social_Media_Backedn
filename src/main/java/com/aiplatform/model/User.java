package com.aiplatform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "full_name")
    private String fullName;

    @Column(nullable = false)
    private String password;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_tier")
    private SubscriptionTier subscriptionTier = SubscriptionTier.FREE;

    @Builder.Default
    @Column(name = "monthly_credits")
    private Double monthlyCredits = 10.0;

    @Builder.Default
    @Column(name = "bonus_credits")
    private Double bonusCredits = 0.0;

    @Column(name = "referral_code", unique = true)
    private String referralCode;

    @Column(name = "referred_by")
    private String referredBy;

    @Builder.Default
    @Column(name = "daily_ads_viewed")
    private Integer dailyAdsViewed = 0;

    @Column(name = "last_ad_viewed_at")
    private LocalDateTime lastAdViewedAt;

    @Column(name = "last_ad_started_at")
    private LocalDateTime lastAdStartedAt;

    @Column(name = "registration_ip")
    private String registrationIp;

    @Column(name = "device_fingerprint")
    private String deviceFingerprint;

    @Builder.Default
    @Column(name = "is_fraud_flagged")
    private Boolean isFraudFlagged = false;

    @Builder.Default
    @Column(name = "referral_status")
    private String referralStatus = "PENDING";

    @Builder.Default
    @Column(name = "daily_credits_used")
    private Double dailyCreditsUsed = 0.0;

    @Column(name = "last_generation_at")
    private LocalDateTime lastGenerationAt;

    @Builder.Default
    @Column(name = "last_reset_at")
    private LocalDateTime lastResetAt = LocalDateTime.now();

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    private Set<String> roles = new java.util.HashSet<>();

    @Column(name = "subscription_expires_at")
    private LocalDateTime subscriptionExpiresAt;

    @Builder.Default
    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts = 0;

    @Column(name = "lock_time")
    private LocalDateTime lockTime;

    @Builder.Default
    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = true; // Set to true for existing users

    @Column(name = "verification_token")
    private String verificationToken;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_purchased_models", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "model_id")
    private java.util.Set<String> purchasedModelIds = new java.util.HashSet<>();

    @Builder.Default
    @Column(name = "stored_images_count")
    private Integer storedImagesCount = 0;

    @Builder.Default
    @Column(name = "stored_videos_count")
    private Integer storedVideosCount = 0;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Builder.Default
    @Column(name = "login_count")
    private Long loginCount = 0L;

    @Builder.Default
    @Column(name = "total_usage_minutes")
    private Long totalUsageMinutes = 0L;

    // ── Video Limit System ──────────────────────────────────────────────────────
    /** Monthly video generation limit from the subscription plan (0 = no video access) */
    @Builder.Default
    @Column(name = "monthly_video_limit")
    private Integer monthlyVideoLimit = 0;

    /** Number of videos generated in the current billing month */
    @Builder.Default
    @Column(name = "videos_used_this_month")
    private Integer videosUsedThisMonth = 0;

    /** When the monthly video counter was last reset */
    @Column(name = "video_reset_date")
    private LocalDateTime videoResetDate;

    // ── Video Credit Wallet ─────────────────────────────────────────────────────
    /** Purchased Veo Lite video credits (never expire) */
    @Builder.Default
    @Column(name = "video_credits_lite")
    private Integer videoCreditLite = 0;

    /** Purchased Veo Fast video credits (never expire) */
    @Builder.Default
    @Column(name = "video_credits_fast")
    private Integer videoCreditFast = 0;

    /** Purchased Veo Standard video credits (never expire) */
    @Builder.Default
    @Column(name = "video_credits_standard")
    private Integer videoCreditStandard = 0;

    // ── Password Reset ──────────────────────────────────────────────────────────
    @Column(name = "password_reset_token")
    private String passwordResetToken;

    @Column(name = "password_reset_token_expiry")
    private LocalDateTime passwordResetTokenExpiry;

    // ── Login Security Tracking ─────────────────────────────────────────────────
    @Column(name = "last_login_ip")
    private String lastLoginIp;

    @Column(name = "last_login_user_agent", columnDefinition = "TEXT")
    private String lastLoginUserAgent;
}
