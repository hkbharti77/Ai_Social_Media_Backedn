package com.aiplatform.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class RateLimitingService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingService.class);

    @Autowired(required = false)
    private ProxyManager<byte[]> proxyManager;

    private final Map<String, Bucket> fallbackBuckets = new ConcurrentHashMap<>();

    public Bucket resolveBucket(String key) {
        Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
                // 500 requests per 15 minutes per user/IP
                // A typical dashboard load fires ~8 requests simultaneously,
                // so 100 was too low for normal usage patterns.
                .addLimit(Bandwidth.classic(500, Refill.greedy(500, Duration.ofMinutes(15))))
                .build();

        if (proxyManager != null) {
            try {
                return proxyManager.builder().build(key.getBytes(StandardCharsets.UTF_8), configSupplier);
            } catch (Exception e) {
                log.warn("Failed to resolve bucket from Redis ProxyManager, falling back to in-memory: {}", e.getMessage());
            }
        }

        return fallbackBuckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(500, Refill.greedy(500, Duration.ofMinutes(15))))
                .build());
    }
}
