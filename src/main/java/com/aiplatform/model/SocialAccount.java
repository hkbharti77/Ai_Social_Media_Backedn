package com.aiplatform.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "social_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String platform; // 'FACEBOOK' | 'INSTAGRAM'

    @Column(name = "facebook_user_id")
    private String facebookUserId;

    @Column(name = "encrypted_token", length = 1000)
    private String encryptedAccessToken;

    @Column(name = "page_id")
    private String pageId;

    @Column(name = "ig_business_id")
    private String igBusinessAccountId;

    @Column(name = "account_name")
    private String accountName;

    @Column(name = "profile_picture_url", length = 1000)
    private String profilePictureUrl;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(name = "encrypted_refresh_token", length = 1000)
    private String encryptedRefreshToken;

    @Builder.Default
    @Column(name = "connected_at")
    private LocalDateTime connectedAt = LocalDateTime.now();
}
