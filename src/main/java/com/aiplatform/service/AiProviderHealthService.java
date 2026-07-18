package com.aiplatform.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * AiProviderHealthService - Monitors provider health using a rolling window of metrics.
 */
@Service
public class AiProviderHealthService {
    private final Map<String, HealthWindow> healthMap = new ConcurrentHashMap<>();

    public void recordSuccess(String providerId, long latencyMs) {
        getOrCreateWindow(providerId).record(true, latencyMs);
    }

    public void recordFailure(String providerId) {
        getOrCreateWindow(providerId).record(false, 0);
    }

    public double getDegradationScore(String providerId) {
        return getOrCreateWindow(providerId).computeScore();
    }

    public boolean isQuarantined(String providerId) {
        return getDegradationScore(providerId) > 0.5; // Example threshold
    }

    private HealthWindow getOrCreateWindow(String providerId) {
        return healthMap.computeIfAbsent(providerId, k -> new HealthWindow());
    }

    @Data
    private static class HealthWindow {
        private static final int WINDOW_SIZE = 100;
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong failedRequests = new AtomicLong(0);
        private final DoubleAdder totalLatency = new DoubleAdder();

        public void record(boolean success, long latency) {
            totalRequests.incrementAndGet();
            if (!success) {
                failedRequests.incrementAndGet();
            } else {
                totalLatency.add(latency);
            }
            
            // In a real system, we would implement a circular buffer or rolling window.
            // For this baseline, we use cumulative metrics with decay.
            if (totalRequests.get() > WINDOW_SIZE * 10) {
                reset();
            }
        }

        public double computeScore() {
            if (totalRequests.get() == 0) return 0;
            double errorRate = (double) failedRequests.get() / totalRequests.get();
            double avgLatency = totalLatency.sum() / (totalRequests.get() - failedRequests.get() + 1);
            
            // Score between 0 (Healthy) and 1 (Dead)
            return Math.min(1.0, errorRate * 2 + (avgLatency > 5000 ? 0.5 : 0));
        }

        private void reset() {
            totalRequests.set(0);
            failedRequests.set(0);
            totalLatency.reset();
        }
    }
}
