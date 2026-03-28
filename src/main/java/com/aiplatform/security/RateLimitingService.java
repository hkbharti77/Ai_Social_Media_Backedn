package com.aiplatform.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimitingService {

    public Bucket createNewBucket(String key) {
        // 100 requests per 15 minutes as per documentation
        Bandwidth limit = Bandwidth.classic(100, Refill.greedy(100, Duration.ofMinutes(15)));
        return Bucket.builder().addLimit(limit).build();
    }
}
