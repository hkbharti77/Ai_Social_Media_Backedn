package com.aiplatform.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TokenRefreshException.class)
    public ResponseEntity<Map<String, Object>> handleTokenRefreshException(TokenRefreshException ex, WebRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("timestamp", new Date());
        body.put("message", ex.getMessage());
        body.put("description", request.getDescription(false));

        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> globalExceptionHandler(Exception ex, WebRequest request) {
        Map<String, Object> body = new HashMap<>();
        
        String message = ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred";
        String causeMessage = (ex.getCause() != null && ex.getCause().getMessage() != null) ? ex.getCause().getMessage() : "none";

        // Handle Google AI High Demand (503) specifically
        if (message.contains("high demand") || causeMessage.contains("high demand")) {
            body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
            body.put("timestamp", new Date());
            body.put("message", "The AI engine is currently experiencing high demand. Please try again in a few seconds.");
            body.put("description", request.getDescription(false));
            return new ResponseEntity<>(body, HttpStatus.TOO_MANY_REQUESTS);
        }

        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("timestamp", new Date());
        body.put("message", ex.getClass().getName() + ": " + message);
        body.put("cause", ex.getCause() != null ? ex.getCause().getClass().getName() + ": " + causeMessage : "none");
        body.put("description", request.getDescription(false));

        logger.error("❌ [GlobalException] {}: {} - Cause: {} - URL: {}", 
            ex.getClass().getSimpleName(), message, causeMessage, request.getDescription(false));
        logger.error("Full stack trace: ", ex);

        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
