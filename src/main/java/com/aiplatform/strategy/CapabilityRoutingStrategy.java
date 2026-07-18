package com.aiplatform.strategy;

import com.aiplatform.dto.RoutingContext;
import com.aiplatform.dto.RoutingDecision;
import com.aiplatform.model.ModelCapability;
import com.aiplatform.service.ModelRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CapabilityRoutingStrategy implements RoutingStrategy {
    private final ModelRegistry modelRegistry;

    @Override
    public RoutingDecision apply(RoutingContext context, ModelCapability currentModel) {
        boolean isImageRequest = "IMAGE_GENERATION".equals(context.request().getActionType());
        
        // If image generation is requested but model doesn't support it
        if (isImageRequest && !currentModel.isSupportsImageGeneration()) {
            String fallback = "imagen-4-fast";
            return RoutingDecision.transition(fallback, 
                    String.format("Model %s lacks image support. Switched to %s", currentModel.getModelId(), fallback));
        }

        // If text/json is requested but model is image-only
        if (!isImageRequest && currentModel.isSupportsImageGeneration()) {
             String fallback = modelRegistry.getDefaultModel().getModelId();
             return RoutingDecision.transition(fallback, 
                    String.format("Model %s is image-only. Switched to %s for text task", currentModel.getModelId(), fallback));
        }

        return RoutingDecision.stay(currentModel.getModelId());
    }

    @Override
    public int getOrder() {
        return 20;
    }
}
