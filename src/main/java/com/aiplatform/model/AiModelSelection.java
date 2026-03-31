package com.aiplatform.model;

import lombok.Getter;

@Getter
public enum AiModelSelection {
    GEMINI_2_5_FLASH("gemini-2.5-flash-image", "gemini-2.5-flash-image", 0, 4.0, ApiProtocol.GEMINI),
    GEMINI_3_1_FLASH("gemini-3.1-flash-image", "gemini-3.1-flash-image-preview", 0, 10.0, ApiProtocol.GEMINI),
    IMAGEN_4_FAST("imagen-4-fast", "imagen-4.0-fast-generate-001", 1, 2.0, ApiProtocol.IMAGEN),
    IMAGEN_4_STANDARD("imagen-4-standard", "imagen-4.0-generate-001", 1, 4.0, ApiProtocol.IMAGEN),
    IMAGEN_4_ULTRA("imagen-4-ultra", "imagen-4.0-ultra-generate-001", 2, 6.0, ApiProtocol.IMAGEN),
    GEMINI_3_PRO("gemini-3-pro-image", "gemini-3-pro-image-preview", 2, 14.0, ApiProtocol.GEMINI);

    private final String modelId;
    private final String actualApiModelId;
    private final int requiredLevel;
    private final double creditCost;
    private final ApiProtocol protocol;

    AiModelSelection(String modelId, String actualApiModelId, int requiredLevel, double creditCost, ApiProtocol protocol) {
        this.modelId = modelId;
        this.actualApiModelId = actualApiModelId;
        this.requiredLevel = requiredLevel;
        this.creditCost = creditCost;
        this.protocol = protocol;
    }

    public static AiModelSelection fromModelId(String modelId) {
        for (AiModelSelection model : values()) {
            if (model.getModelId().equals(modelId)) {
                return model;
            }
        }
        // Default to lowest model if unknown
        return GEMINI_2_5_FLASH;
    }
}
