package com.aiplatform.service;

import com.aiplatform.model.SubscriptionTier;
import com.aiplatform.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * AiTierService — Single Responsibility: Tier-based model gating and feature resolution.
 *
 * Enforces subscription tier constraints on AI model selection and resolves
 * content-type role parameters (MARKETING vs EDUCATIONAL).
 *
 * Stateless: Reads only from User's SubscriptionTier enum. No database calls.
 */
@Service
public class AiTierService {

    private static final Logger logger = LoggerFactory.getLogger(AiTierService.class);

    /**
     * Resolves content-type role parameters (MARKETING vs EDUCATIONAL).
     * Used by prompt templates across all AI services.
     */
    public Map<String, String> resolvePurposeParams(String contentType) {
        Map<String, String> params = new HashMap<>();
        if ("EDUCATIONAL".equalsIgnoreCase(contentType)) {
            params.put("role", "Educational Expert & Mentor");
            params.put("type", "educational");
            params.put("purpose", "Educational");
            params.put("entityType", "educational channel");
            params.put("goalDescription", "Value-driven teaching, knowledge sharing, and informative explanation");
        } else {
            params.put("role", "Social Media Expert");
            params.put("type", "marketing");
            params.put("purpose", "Marketing");
            params.put("entityType", "brand");
            params.put("goalDescription", "Brand Awareness, Conversion, and Audience Engagement");
        }
        return params;
    }
}
