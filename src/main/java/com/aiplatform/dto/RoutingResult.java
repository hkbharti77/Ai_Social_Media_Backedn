package com.aiplatform.dto;

import java.util.List;
import java.util.Map;

/**
 * RoutingResult - The final decision with traceability and operational metadata.
 */
public record RoutingResult(
        String selectedModelId,
        List<String> trace,
        long routingLatencyMs,
        Map<String, Object> metadata
) {}
