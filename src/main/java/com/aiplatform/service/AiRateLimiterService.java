package com.aiplatform.service;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AiRateLimiterService - Implements token bucket rate limiting per provider.
 */
@Service
public class AiRateLimiterService {
    private final Map<String, Bucket> providerBuckets = new ConcurrentHashMap<>();

    public boolean tryConsume(String providerId) {
        return getOrCreateBucket(providerId).tryConsume(1);
    }

    private Bucket getOrCreateBucket(String providerId) {
        return providerBuckets.computeIfAbsent(providerId, k -> {
            // Default: 10 requests per minute
            Bandwidth limit = Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1)));
            return Bucket.builder()
                    .addLimit(limit)
                    .build();
        });
    }
}
