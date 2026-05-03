package com.aiplatform.model;

import lombok.Getter;

/**
 * Enum representing available Veo video generation models with their configurations.
 * Similar to AiModelSelection for images, this allows users to choose video quality vs cost.
 */
@Getter
public enum VeoModelSelection {
    // Note: Credit costs are not used anymore - we use video limits instead
    // These are kept for backward compatibility
    VEO_LITE("veo-lite", "veo-3.1-lite-generate-preview", 1, 0.0, "Economy", "Best for social media, cost-effective"),
    VEO_FAST("veo-fast", "veo-3.1-fast-generate-preview", 2, 0.0, "Balanced", "Great quality, faster generation"),
    VEO_STANDARD("veo-standard", "veo-3.1-generate-preview", 3, 0.0, "Premium", "Cinematic quality, professional output");

    private final String modelId;           // User-facing ID (e.g., "veo-lite")
    private final String actualApiModelId;  // Google API model ID
    private final int requiredLevel;        // Minimum subscription tier (0=Free, 1=Standard, 2=Pro)
    private final double creditCost;        // Credits per 8-second video
    private final String qualityTier;       // Display name for quality
    private final String description;       // User-friendly description

    VeoModelSelection(String modelId, String actualApiModelId, int requiredLevel, 
                      double creditCost, String qualityTier, String description) {
        this.modelId = modelId;
        this.actualApiModelId = actualApiModelId;
        this.requiredLevel = requiredLevel;
        this.creditCost = creditCost;
        this.qualityTier = qualityTier;
        this.description = description;
    }

    /**
     * Get VeoModelSelection from user-provided modelId
     * @param modelId User-facing model ID (e.g., "veo-lite", "veo-fast")
     * @return Matching VeoModelSelection or default to VEO_LITE
     */
    public static VeoModelSelection fromModelId(String modelId) {
        if (modelId == null || modelId.trim().isEmpty()) {
            return VEO_LITE; // Default to cheapest
        }
        
        for (VeoModelSelection model : values()) {
            if (model.getModelId().equalsIgnoreCase(modelId)) {
                return model;
            }
        }
        
        // Default to VEO_LITE if unknown model
        return VEO_LITE;
    }

    /**
     * Check if user's subscription tier allows this model
     * @param tierName User's subscription tier name (Free, Standard, Pro, Super Pro)
     * @return true if user can access this model
     */
    public boolean isAccessibleByTier(String tierName) {
        if (tierName == null) return false;
        
        int userLevel = getTierLevel(tierName);
        return userLevel >= this.requiredLevel;
    }
    
    /**
     * Convert tier name to level
     * @param tierName Subscription tier name
     * @return Tier level (0=Free, 1=Standard, 2=Pro, 3=SuperPro)
     */
    private static int getTierLevel(String tierName) {
        return switch (tierName.toLowerCase()) {
            case "free" -> 0;
            case "standard" -> 1;
            case "pro" -> 2;
            case "super pro", "superpro" -> 3;
            default -> 0;
        };
    }
}
