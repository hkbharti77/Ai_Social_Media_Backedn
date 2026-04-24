package com.aiplatform.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class RateLimitingService {

    private final ProxyManager<byte[]> proxyManager;

    public Bucket resolveBucket(String key) {
        Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
                // 500 requests per 15 minutes per user/IP
                // A typical dashboard load fires ~8 requests simultaneously,
                // so 100 was too low for normal usage patterns.
                .addLimit(Bandwidth.classic(500, Refill.greedy(500, Duration.ofMinutes(15))))
                .build();
        return proxyManager.builder().build(key.getBytes(StandardCharsets.UTF_8), configSupplier);
    }
}
