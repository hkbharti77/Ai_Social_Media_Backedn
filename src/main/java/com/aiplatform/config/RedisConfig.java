package com.aiplatform.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Value("${spring.data.redis.url:${REDIS_URL:}}")
    private String redisUrl;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisConfiguration redisConfiguration;
        if (redisUrl != null && !redisUrl.trim().isEmpty()) {
            redisConfiguration = LettuceConnectionFactory.createRedisConfiguration(redisUrl.trim());
        } else {
            RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(redisHost, redisPort);
            if (redisPassword != null && !redisPassword.trim().isEmpty()) {
                standalone.setPassword(RedisPassword.of(redisPassword.trim()));
            }
            redisConfiguration = standalone;
        }
        return new LettuceConnectionFactory(redisConfiguration);
    }

    @Bean(destroyMethod = "shutdown")
    public RedisClient redisClient() {
        RedisURI redisUri;

        if (redisUrl != null && !redisUrl.trim().isEmpty()) {
            log.info("Connecting to Redis using URL: {}", maskUrl(redisUrl));
            redisUri = RedisURI.create(redisUrl.trim());
            redisUri.setTimeout(Duration.ofSeconds(10));
        } else {
            log.info("Connecting to Redis using host={}:{}", redisHost, redisPort);
            RedisURI.Builder builder = RedisURI.builder()
                    .withHost(redisHost)
                    .withPort(redisPort)
                    .withTimeout(Duration.ofSeconds(10));

            if (redisPassword != null && !redisPassword.trim().isEmpty()) {
                builder.withPassword(redisPassword.trim().toCharArray());
            }
            redisUri = builder.build();
        }

        return RedisClient.create(redisUri);
    }

    @Bean
    public ProxyManager<byte[]> proxyManager(RedisClient redisClient) {
        try {
            return LettuceBasedProxyManager.builderFor(redisClient)
                    .withExpirationStrategy((state, currentTimeNanos) -> Duration.ofDays(1).toNanos())
                    .build();
        } catch (Exception e) {
            log.warn("⚠️ Unable to connect to Redis for RateLimiting ProxyManager ({}). Application will use in-memory rate limiting fallback.", e.getMessage());
            return null;
        }
    }

    private String maskUrl(String url) {
        return url.replaceAll("://([^:]+):([^@]+)@", "://$1:***@")
                  .replaceAll("://:([^@]+)@", "://:***@");
    }
}
