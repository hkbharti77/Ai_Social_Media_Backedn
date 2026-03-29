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
    private Integer monthlyCredits = 10;

    @Builder.Default
    @Column(name = "daily_credits_used")
    private Integer dailyCreditsUsed = 0;

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
}
