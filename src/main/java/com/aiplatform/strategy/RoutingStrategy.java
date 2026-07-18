package com.aiplatform.strategy;

import com.aiplatform.dto.RoutingContext;
import com.aiplatform.dto.RoutingDecision;
import com.aiplatform.model.ModelCapability;

/**
 * RoutingStrategy - Interface for individual routing policies with traceability.
 */
public interface RoutingStrategy {
    /**
     * Evaluates the context and returns a decision.
     */
    RoutingDecision apply(RoutingContext context, ModelCapability currentModel);
    
    /**
     * Determines execution order (Pipeline precedence).
     */
    int getOrder();
}
