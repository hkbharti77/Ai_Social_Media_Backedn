package com.aiplatform.service;

import com.aiplatform.model.ModelCapability;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class ModelRegistry {
    private final Map<String, ModelCapability> registry = new HashMap<>();

    public ModelRegistry() {
        // Chat Models
        register(ModelCapability.builder()
                .modelId("gemini-1.5-flash")
                .actualApiModelId("gemini-1.5-flash-001")
                .supportsJson(true)
                .supportsVision(true)
                .supportsImageGeneration(false)
                .maxTokens(1048576)
                .creditCostFactor(1.0)
                .minTierLevel(0)
                .isDefault(true)
                .build());

        register(ModelCapability.builder()
                .modelId("gemini-1.5-pro")
                .actualApiModelId("gemini-1.5-pro-001")
                .supportsJson(true)
                .supportsVision(true)
                .supportsImageGeneration(false)
                .maxTokens(2097152)
                .creditCostFactor(5.0)
                .minTierLevel(2)
                .build());

        // Image Models
        register(ModelCapability.builder()
                .modelId("imagen-4-fast")
                .actualApiModelId("imagen-4.0-fast-generate-001")
                .supportsVision(false)
                .supportsImageGeneration(true)
                .creditCostFactor(2.0)
                .minTierLevel(1)
                .build());

        register(ModelCapability.builder()
                .modelId("imagen-4-standard")
                .actualApiModelId("imagen-4.0-generate-001")
                .supportsVision(false)
                .supportsImageGeneration(true)
                .creditCostFactor(4.0)
                .minTierLevel(1)
                .build());

        register(ModelCapability.builder()
                .modelId("imagen-4-ultra")
                .actualApiModelId("imagen-4.0-ultra-generate-001")
                .supportsVision(false)
                .supportsImageGeneration(true)
                .creditCostFactor(6.0)
                .minTierLevel(2)
                .build());
    }

    private void register(ModelCapability capability) {
        registry.put(capability.getModelId(), capability);
    }

    public Optional<ModelCapability> getCapability(String modelId) {
        return Optional.ofNullable(registry.get(modelId));
    }

    public ModelCapability getDefaultModel() {
        return registry.values().stream()
                .filter(ModelCapability::isDefault)
                .findFirst()
                .orElse(registry.get("gemini-1.5-flash"));
    }
}
