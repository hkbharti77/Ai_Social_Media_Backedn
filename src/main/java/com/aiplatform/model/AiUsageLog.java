package com.aiplatform.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_usage_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiUsageLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "roles", "socialAccounts"})
    private User user;

    @Column(nullable = false)
    private String modelId; // e.g., gemini-1.5-flash

    @Column(nullable = false)
    private String actionType; // e.g., POST_GENERATION, IMAGE_GENERATION

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(columnDefinition = "TEXT")
    @JsonProperty("promptText")
    private String prompt;

    @Column(name = "result_url")
    private String resultUrl; // For image/video generation

    @Column(name = "feature_name")
    private String featureName; // e.g., "Post Generator", "Meme"
}
