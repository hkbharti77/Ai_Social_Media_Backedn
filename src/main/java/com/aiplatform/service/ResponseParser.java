package com.aiplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResponseParser {
    private static final Logger logger = LoggerFactory.getLogger(ResponseParser.class);
    private final ObjectMapper objectMapper;

    public String extractJson(String rawResponse) {
        if (rawResponse == null) return null;
        
        String processed = rawResponse.trim();
        
        if (processed.contains("```json")) {
            processed = processed.substring(processed.indexOf("```json") + 7, processed.lastIndexOf("```")).trim();
        } else if (processed.contains("```")) {
            processed = processed.substring(processed.indexOf("```") + 3, processed.lastIndexOf("```")).trim();
        }
        
        return processed;
    }

    public <T> T parse(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            logger.error("Failed to parse JSON into {}: {}", clazz.getSimpleName(), e.getMessage());
            throw new RuntimeException("Parsing failure: " + e.getMessage(), e);
        }
    }

    public JsonNode parseAsNode(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            logger.error("Failed to parse JSON into JsonNode: {}", e.getMessage());
            throw new RuntimeException("JsonNode parsing failure: " + e.getMessage(), e);
        }
    }
}
