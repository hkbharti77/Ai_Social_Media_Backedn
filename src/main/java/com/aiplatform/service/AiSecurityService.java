package com.aiplatform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AiSecurityService — Single Responsibility: Prompt injection prevention and response parsing.
 *
 * All input sanitization and AI response extraction logic lives here.
 * Stateless: No database calls, no external dependencies.
 */
@Service
public class AiSecurityService {

    private static final Logger logger = LoggerFactory.getLogger(AiSecurityService.class);

    /**
     * Detects and neutralizes prompt injection attempts.
     * Wraps safe input in data boundaries to prevent LLM confusion.
     */
    public String guardInput(String input) {
        if (input == null) return "";
        String cleaned = input.replaceAll("[\\x00-\\x1F\\x7F]", "");

        String lower = cleaned.toLowerCase();
        if (lower.contains("ignore previous") || lower.contains("system prompt") ||
            lower.contains("disregard all") || lower.contains("forget everything") ||
            lower.contains("you are now") || lower.contains("bypass") ||
            cleaned.contains("---") || cleaned.contains("===")) {

            logger.warn("⚠️ CRITICAL: Potential Prompt Injection neutralized: {}", cleaned);
            return "[SECURE_DATA_INPUT_ONLY]";
        }

        return String.format("<data_boundary>%s</data_boundary>", cleaned.trim());
    }

    /**
     * Extracts clean JSON from AI model responses.
     * Handles markdown fences (```json ... ```) and raw JSON extraction.
     */
    public String extractJsonResponse(String content) {
        if (content == null || content.isEmpty()) return "{}";

        if (content.contains("```json")) {
            int start = content.indexOf("```json") + 7;
            int end = content.lastIndexOf("```");
            if (end > start) return content.substring(start, end).trim();
        } else if (content.contains("```")) {
            int start = content.indexOf("```") + 3;
            int end = content.lastIndexOf("```");
            if (end > start) return content.substring(start, end).trim();
        }

        int firstBrace = content.indexOf('{');
        int lastBrace = content.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return content.substring(firstBrace, lastBrace + 1).trim();
        }

        return content.trim();
    }
}
