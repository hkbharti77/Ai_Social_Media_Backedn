package com.aiplatform.strategy;

import com.aiplatform.dto.RoutingContext;
import com.aiplatform.dto.RoutingDecision;
import com.aiplatform.model.ModelCapability;
import com.aiplatform.model.SubscriptionTier;
import com.aiplatform.service.ModelRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TierRoutingStrategy implements RoutingStrategy {
    private final ModelRegistry modelRegistry;

    @Override
    public RoutingDecision apply(RoutingContext context, ModelCapability currentModel) {
        if (context.user() == null) return RoutingDecision.stay(currentModel.getModelId());

        SubscriptionTier tier = context.user().getSubscriptionTier();
        int userLevel = (tier != null) ? tier.getLevel() : 0;

        if (currentModel.getMinTierLevel() > userLevel) {
            ModelCapability fallback = modelRegistry.getDefaultModel();
            String reason = String.format("Downgraded %s -> %s (User Tier %s < Required Level %d)",
                    currentModel.getModelId(), fallback.getModelId(), tier, currentModel.getMinTierLevel());
            
            return RoutingDecision.transition(fallback.getModelId(), reason);
        }

        return RoutingDecision.stay(currentModel.getModelId());
    }

    @Override
    public int getOrder() {
        return 10;
    }
}
