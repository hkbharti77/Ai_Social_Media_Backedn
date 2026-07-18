package com.aiplatform.service;

import com.aiplatform.dto.AiRequest;
import com.aiplatform.exception.AiOverloadException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

@Service
@RequiredArgsConstructor
public class AiOrchestrator {
    private static final Logger logger = LoggerFactory.getLogger(AiOrchestrator.class);

    private final PromptLoader promptLoader;
    private final PromptEngine promptEngine;
    private final ResponseParser responseParser;
    private final AiRetryManager retryManager;
    private final ProviderRouter providerRouter;
    private final AiMetricsService metricsService;
    private final ObservationRegistry observationRegistry;
    private final CircuitBreakerFactory circuitBreakerFactory;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.google.genai.api-key}")
    private String apiKey;

    @Value("${spring.ai.google.genai.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String apiUrl;

    public <T> T generateJson(AiRequest request, Class<T> clazz) {
        return Observation.createNotStarted("ai.orchestration.generate", observationRegistry)
                .lowCardinalityKeyValue("action", request.getActionType())
                .observe(() -> {
                    return retryManager.executeWithRetry("GenerateJson-" + clazz.getSimpleName(), () -> {
            long startTime = System.currentTimeMillis();
            try {
                String finalModelId = providerRouter.route(request).selectedModelId();
                
                // Distributed Tracing + Circuit Breaker
                String rawResponse = circuitBreakerFactory.create(finalModelId).run(
                    () -> executeChat(request, finalModelId),
                    throwable -> {
                        logger.warn("🔌 Circuit Breaker OPEN for {}. Error: {}", finalModelId, throwable.getMessage());
                        throw new RuntimeException("AI Provider currently unavailable via circuit breaker", throwable);
                    }
                );
                
                String json = responseParser.extractJson(rawResponse);
                T result = responseParser.parse(json, clazz);
                
                metricsService.recordExecutionTime("GenerateJson", finalModelId, System.currentTimeMillis() - startTime);
                return result;
            } catch (RejectedExecutionException e) {
                metricsService.recordError("GenerateJson", "N/A", "REJECTED_OVERLOAD");
                logger.error("🛑 AI Overload: Request rejected by task executor queue. [Type: {}]", request.getTaskType());
                throw new AiOverloadException("AI systems are currently under heavy load. Please retry in 5 seconds.");
            } catch (Exception e) {
                metricsService.recordError("GenerateJson", "N/A", e.getClass().getSimpleName());
                throw e;
            }
        });
    });
    }

    public JsonNode generateJsonNode(AiRequest request) {
        return Observation.createNotStarted("ai.orchestration.generate.node", observationRegistry)
                .observe(() -> {
                    return retryManager.executeWithRetry("GenerateJsonNode", () -> {
            long startTime = System.currentTimeMillis();
            try {
                String finalModelId = providerRouter.route(request).selectedModelId();
                
                String rawResponse = circuitBreakerFactory.create(finalModelId).run(
                    () -> executeChat(request, finalModelId),
                    throwable -> { throw new RuntimeException("Circuit breaker open", throwable); }
                );
                
                String json = responseParser.extractJson(rawResponse);
                JsonNode result = responseParser.parseAsNode(json);
                
                metricsService.recordExecutionTime("GenerateJsonNode", finalModelId, System.currentTimeMillis() - startTime);
                return result;
            } catch (RejectedExecutionException e) {
                metricsService.recordError("GenerateJsonNode", "N/A", "REJECTED_OVERLOAD");
                throw new AiOverloadException("AI systems are currently under heavy load.");
            } catch (Exception e) {
                metricsService.recordError("GenerateJsonNode", "N/A", e.getClass().getSimpleName());
                throw e;
            }
        });
    });
    }

    public String generateText(AiRequest request) {
        return Observation.createNotStarted("ai.orchestration.generate.text", observationRegistry)
                .observe(() -> {
                    return retryManager.executeWithRetry("GenerateText", () -> {
            long startTime = System.currentTimeMillis();
            try {
                String finalModelId = providerRouter.route(request).selectedModelId();
                
                String result = circuitBreakerFactory.create(finalModelId).run(
                    () -> executeChat(request, finalModelId),
                    throwable -> { throw new RuntimeException("Circuit breaker open", throwable); }
                );
                
                metricsService.recordExecutionTime("GenerateText", finalModelId, System.currentTimeMillis() - startTime);
                return result;
            } catch (RejectedExecutionException e) {
                metricsService.recordError("GenerateText", "N/A", "REJECTED_OVERLOAD");
                throw new AiOverloadException("AI systems are currently under heavy load.");
            } catch (Exception e) {
                metricsService.recordError("GenerateText", "N/A", e.getClass().getSimpleName());
                throw e;
            }
        });
    });
    }

    private String executeChat(AiRequest request, String modelId) {
        try {
            Prompt prompt = promptEngine.buildPrompt(request);
            String actualModel = modelId;
            
            // Normalize model ID for REST API if needed
            if (actualModel.contains("flash") && !actualModel.contains("001")) {
                actualModel = "gemini-1.5-flash-001";
            } else if (actualModel.contains("pro") && !actualModel.contains("001")) {
                actualModel = "gemini-1.5-pro-001";
            }

            String url = String.format("%s/models/%s:generateContent?key=%s", apiUrl, actualModel, apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            
            if (request.getCacheId() != null) {
                requestBody.put("cachedContent", request.getCacheId());
            }

            Map<String, Object> part = new HashMap<>();
            part.put("text", prompt.getContents());

            Map<String, Object> content = new HashMap<>();
            content.put("role", "user");
            content.put("parts", Collections.singletonList(part));

            requestBody.put("contents", Collections.singletonList(content));

            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", request.getTemperature());
            generationConfig.put("responseMimeType", "application/json");
            requestBody.put("generationConfig", generationConfig);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            logger.info("🚀 Dispatching Gemini API Call [Model: {}] for Action: {}", actualModel, request.getActionType());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            }
            
            throw new RuntimeException("Gemini API call failed with status: " + response.getStatusCode());
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            String body = e.getResponseBodyAsString();
            logger.error("❌ Gemini API Auth or Quota Error [{}]: {}", e.getStatusCode(), body);
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new RuntimeException("AI Platform Authentication Failed (API Key Expired or Invalid).", e);
            }
            throw new RuntimeException("Gemini API Error: " + e.getStatusCode() + " - " + body, e);
        } catch (Exception e) {
            logger.error("❌ Unexpected error during Gemini Chat execution: {}", e.getMessage());
            throw new RuntimeException("AI Chat execution failed.", e);
        }
    }
}
