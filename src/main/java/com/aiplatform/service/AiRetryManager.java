package com.aiplatform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class AiRetryManager {
    private static final Logger logger = LoggerFactory.getLogger(AiRetryManager.class);
    private static final int MAX_RETRIES = 3;

    public <T> T executeWithRetry(String actionName, Supplier<T> action) {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < MAX_RETRIES) {
            try {
                return action.get();
            } catch (Exception e) {
                attempts++;
                lastException = e;
                logger.warn("?? Action '{}' failed (Attempt {}/{}). Error: {}", 
                            actionName, attempts, MAX_RETRIES, e.getMessage());
                
                if (attempts < MAX_RETRIES) {
                    try {
                        Thread.sleep(1000L * attempts); // Exponential-ish backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                }
            }
        }
        
        logger.error("?? All {} attempts failed for action '{}'.", MAX_RETRIES, actionName);
        throw new RuntimeException("Action '" + actionName + "' failed after " + MAX_RETRIES + " attempts", lastException);
    }
}
