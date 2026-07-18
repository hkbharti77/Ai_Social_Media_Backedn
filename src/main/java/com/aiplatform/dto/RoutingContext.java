package com.aiplatform.dto;

import com.aiplatform.model.User;

/**
 * RoutingContext - Immutable state passed to each strategy.
 */
public record RoutingContext(
        AiRequest request,
        User user,
        String initialModelId
) {}
