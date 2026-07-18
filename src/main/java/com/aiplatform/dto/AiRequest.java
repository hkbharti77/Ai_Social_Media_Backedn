package com.aiplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Encapsulates all context needed for an AI generation request.
 * Decouples business logic from Spring AI implementation details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRequest {
    private String template;
    private String promptPath;
    private Map<String, Object> templateParams;
    private Long userId;
    private String modelId;
    private String actionType; // e.g., "POST_GENERATION"
    private String userCommand; // For logging
    private String cacheId;
    private String correlationId;
    
    @Builder.Default
    private com.aiplatform.model.AiTaskType taskType = com.aiplatform.model.AiTaskType.SYNC_USER_REQUEST;
    
    @Builder.Default
    private double temperature = 0.7;
    
    @Builder.Default
    private int maxRetries = 1;
}
