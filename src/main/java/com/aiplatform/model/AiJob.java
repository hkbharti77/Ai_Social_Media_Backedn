package com.aiplatform.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AiJob - Durable representation of an AI task for persistence and recovery.
 */
@Entity
@Table(name = "ai_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    private AiTaskType taskType;

    @Enumerated(EnumType.STRING)
    private JobStatus status;

    private int retryCount;
    private int maxRetries;

    @Column(columnDefinition = "TEXT")
    private String requestPayloadJson;

    @Column(columnDefinition = "TEXT")
    private String resultJson;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime nextRetryAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (maxRetries == 0) maxRetries = 3;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum JobStatus {
        PENDING, RUNNING, COMPLETED, FAILED, DEAD_LETTER
    }
}
