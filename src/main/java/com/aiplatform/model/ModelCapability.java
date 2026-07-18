package com.aiplatform.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModelCapability {
    private String modelId;
    private String actualApiModelId;
    private boolean supportsJson;
    private boolean supportsVision;
    private boolean supportsImageGeneration;
    private int maxTokens;
    private double creditCostFactor;
    private int minTierLevel;
    private boolean isDefault;
}
