package com.aiplatform.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * AiMetricsService - Tracks operational metrics for AI orchestration and routing.
 * Ready for Prometheus / Grafana.
 */
@Service
@RequiredArgsConstructor
public class AiMetricsService {
    private final MeterRegistry meterRegistry;

    public void recordRoutingDecision(String strategyName, String modelId) {
        meterRegistry.counter("ai.routing.decisions", "strategy", strategyName, "model", modelId).increment();
    }

    public void recordDowngrade(String fromModel, String toModel, String reason) {
        meterRegistry.counter("ai.routing.downgrades", "from", fromModel, "to", toModel, "reason", reason).increment();
    }

    public void recordCapabilityFallback(String requestedAction, String fallbackModel) {
        meterRegistry.counter("ai.routing.capability.fallback", "action", requestedAction, "fallback", fallbackModel).increment();
    }

    public void recordExecutionTime(String operation, String modelId, long timeMs) {
        Timer.builder("ai.orchestration.execution.time")
                .tag("operation", operation)
                .tag("model", modelId)
                .register(meterRegistry)
                .record(timeMs, TimeUnit.MILLISECONDS);
    }
    
    public void recordError(String operation, String modelId, String errorType) {
        meterRegistry.counter("ai.orchestration.errors", "operation", operation, "model", modelId, "type", errorType).increment();
    }

    public void recordQueueSaturation(String executorName, double ratio) {
        meterRegistry.gauge("ai.executor.saturation", io.micrometer.core.instrument.Tags.of("executor", executorName), ratio);
    }
}
