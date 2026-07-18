package com.aiplatform.service;

import com.aiplatform.model.BusinessProfile;
import com.aiplatform.repository.BusinessProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * AiCacheService — Single Responsibility: Gemini API Context Caching.
 *
 * Manages the creation, resolution, and usage of cached content (like brand context
 * or scraped websites) to optimize tokens and latency for Gemini API calls.
 */
@Service
@RequiredArgsConstructor
public class AiCacheService {

    private static final Logger logger = LoggerFactory.getLogger(AiCacheService.class);

    private final GeminiCacheService geminiCacheService;
    private final BusinessProfileRepository businessProfileRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.google.genai.api-key}")
    private String apiKey;

    @Value("${spring.ai.google.genai.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String apiUrl;

    /**
     * Resolves or creates a Gemini cache for Brand Identity context.
     * Caches the visual and voice persona for 1 hour to save tokens on repeated requests.
     */
    public String resolveGeminiCacheId(BusinessProfile bp, String modelId, String currentContent) {
        if (currentContent == null || currentContent.length() < 3000) {
            return null;
        }

        String currentHash = Integer.toHexString(currentContent.hashCode());

        // Check if cache exists, is not expired, and content hash matches
        if (bp.getGeminiCacheId() != null &&
            bp.getGeminiCacheExpiry() != null &&
            bp.getGeminiCacheExpiry().isAfter(LocalDateTime.now()) &&
            currentHash.equals(bp.getGeminiCacheContentHash())) {
            return bp.getGeminiCacheId();
        }

        String cacheModelId = "gemini-1.5-flash-001";
        if (modelId != null && modelId.contains("pro")) {
            cacheModelId = "gemini-1.5-pro-001";
        }

        String newCacheId = geminiCacheService.createCache(cacheModelId, currentContent, 3600);

        if (newCacheId != null) {
            bp.setGeminiCacheId(newCacheId);
            bp.setGeminiCacheExpiry(LocalDateTime.now().plusHours(1));
            bp.setGeminiCacheContentHash(currentHash);
            businessProfileRepository.save(bp);
            return newCacheId;
        }

        return null;
    }

    /**
     * Resolves or creates a cache for Repurposing content (e.g. scraped website text).
     * Caches the scraped data for 30 minutes.
     */
    public String resolveRepurposeCacheId(BusinessProfile bp, String content, String modelId) {
        if (content == null || content.length() < 3000) return null;

        String currentHash = Integer.toHexString(content.hashCode());

        if (bp.getLastScrapedCacheId() != null &&
            bp.getLastScrapedExpiry() != null &&
            bp.getLastScrapedExpiry().isAfter(LocalDateTime.now()) &&
            currentHash.equals(bp.getLastScrapedHash())) {
            return bp.getLastScrapedCacheId();
        }

        String cacheModelId = modelId.contains("pro") ? "gemini-1.5-pro-001" : "gemini-1.5-flash-001";
        String newCacheId = geminiCacheService.createCache(cacheModelId, content, 1800);

        if (newCacheId != null) {
            bp.setLastScrapedCacheId(newCacheId);
            bp.setLastScrapedExpiry(LocalDateTime.now().plusMinutes(30));
            bp.setLastScrapedHash(currentHash);
            businessProfileRepository.save(bp);
            return newCacheId;
        }

        return null;
    }

    /**
     * Makes a direct REST call to Gemini API utilizing a cached context block.
     * Fallback mechanism when Spring AI abstractions don't natively support context caching.
     */
    public String callGeminiApiWithCache(String cacheName, Prompt prompt, String modelId, double temperature) {
        try {
            String actualModel = modelId;
            if (actualModel.contains("flash")) actualModel = "gemini-1.5-flash-001";
            else if (actualModel.contains("pro")) actualModel = "gemini-1.5-pro-001";

            String url = String.format("%s/models/%s:generateContent?key=%s", apiUrl, actualModel, apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("cachedContent", cacheName);

            Map<String, Object> part = new HashMap<>();
            part.put("text", prompt.getContents());

            Map<String, Object> content = new HashMap<>();
            content.put("role", "user");
            content.put("parts", Collections.singletonList(part));

            requestBody.put("contents", Collections.singletonList(content));

            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", temperature);
            generationConfig.put("responseMimeType", "application/json");
            requestBody.put("generationConfig", generationConfig);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            logger.info("📦 Dispatching Direct Gemini Chat with Cache: {}", cacheName);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                return root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            }
            throw new RuntimeException("Gemini Direct API failed: " + response.getStatusCode());
        } catch (Exception e) {
            logger.error("❌ Direct Gemini API call failed: {}", e.getMessage());
            throw new RuntimeException("AI Chat with cache failed.", e);
        }
    }
}
