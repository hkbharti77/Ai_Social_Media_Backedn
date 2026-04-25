package com.aiplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GeminiCacheService {
    private static final Logger logger = LoggerFactory.getLogger(GeminiCacheService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.google.genai.api-key}")
    private String apiKey;

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/cachedContents";

    public String createCache(String modelId, String contextContent, int ttlSeconds) {
        // Ensure modelId has the correct prefix for the REST API
        String actualModel = modelId;
        if (!actualModel.startsWith("models/")) {
            actualModel = "models/" + actualModel;
        }

        String url = BASE_URL + "?key=" + apiKey;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", actualModel);
        
        // Context Caching stores "contents" which are usually the system instructions or large documents
        Map<String, Object> part = new HashMap<>();
        part.put("text", contextContent);

        Map<String, Object> content = new HashMap<>();
        content.put("role", "user");
        content.put("parts", Collections.singletonList(part));

        requestBody.put("contents", Collections.singletonList(content));
        requestBody.put("ttl", ttlSeconds + "s");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            logger.info("📦 Creating Gemini Context Cache for model: {}", actualModel);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String cacheName = root.path("name").asText();
                logger.info("✅ Cache created successfully: {}", cacheName);
                return cacheName;
            } else {
                logger.error("❌ Failed to create Gemini Cache. Status: {}, Body: {}", response.getStatusCode(), response.getBody());
                return null;
            }
        } catch (Exception e) {
            logger.error("❌ Exception during Gemini Cache creation: {}", e.getMessage());
            // If the error is about token threshold, we should handle it gracefully
            if (e.getMessage().contains("400") && e.getMessage().contains("token")) {
                logger.warn("⚠️ Context too small for caching (Minimum ~2048 tokens required).");
            }
            return null;
        }
    }

    public void deleteCache(String cacheName) {
        if (cacheName == null || cacheName.isEmpty()) return;
        
        String url = String.format("%s/%s?key=%s", BASE_URL, cacheName.replace("cachedContents/", ""), apiKey);
        
        try {
            logger.info("🗑️ Deleting Gemini Context Cache: {}", cacheName);
            restTemplate.delete(url);
        } catch (Exception e) {
            logger.warn("⚠️ Failed to delete Gemini Cache {}: {}", cacheName, e.getMessage());
        }
    }
}
