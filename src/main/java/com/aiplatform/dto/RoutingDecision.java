package com.aiplatform.dto;

import java.util.Optional;

/**
 * RoutingDecision - A single step in the routing pipeline.
 */
public record RoutingDecision(
        String modelId,
        String traceMessage
) {
    public static RoutingDecision stay(String modelId) {
        return new RoutingDecision(modelId, null);
    }
    
    public static RoutingDecision transition(String modelId, String reason) {
        return new RoutingDecision(modelId, reason);
    }
}
