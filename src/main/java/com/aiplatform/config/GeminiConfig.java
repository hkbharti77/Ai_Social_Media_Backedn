package com.aiplatform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class GeminiConfig {

    /**
     * Shared RestTemplate with explicit connect/read timeouts.
     * Without these, a single hanging external API call (Facebook, Gemini, etc.)
     * can block a thread indefinitely.
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);  // 10 seconds to establish connection
        factory.setReadTimeout(90_000);     // 90 seconds - Image generation can be slow
        return new RestTemplate(factory);
    }
}
