package com.aiplatform.exception;

import com.aiplatform.dto.StandardErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AiOverloadException.class)
    public ResponseEntity<StandardErrorResponse> handleAiOverloadException(AiOverloadException ex, WebRequest request) {
        StandardErrorResponse errorResponse = StandardErrorResponse.builder()
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .message(ex.getMessage())
                .path(((ServletWebRequest)request).getRequest().getRequestURI())
                .timestamp(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString())
                .build();

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(errorResponse);
    }

    @ExceptionHandler(TokenRefreshException.class)
    public ResponseEntity<StandardErrorResponse> handleTokenRefreshException(TokenRefreshException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(InsufficientCreditsException.class)
    public ResponseEntity<StandardErrorResponse> handleInsufficientCreditsException(InsufficientCreditsException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.PAYMENT_REQUIRED, ex.getMessage(), request);
    }

    @ExceptionHandler(StorageLimitExceededException.class)
    public ResponseEntity<StandardErrorResponse> handleStorageLimitExceededException(StorageLimitExceededException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.PAYMENT_REQUIRED, ex.getMessage(), request);
    }

    @ExceptionHandler(VeoRateLimitException.class)
    public ResponseEntity<StandardErrorResponse> handleVeoRateLimitException(VeoRateLimitException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request);
    }

    @ExceptionHandler(VeoTimeoutException.class)
    public ResponseEntity<StandardErrorResponse> handleVeoTimeoutException(VeoTimeoutException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.GATEWAY_TIMEOUT, ex.getMessage(), request);
    }

    @ExceptionHandler(VeoGenerationException.class)
    public ResponseEntity<StandardErrorResponse> handleVeoGenerationException(VeoGenerationException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.BAD_GATEWAY, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<StandardErrorResponse> globalExceptionHandler(Exception ex, WebRequest request) {
        String message = ex.getMessage() != null ? ex.getMessage() : "An unexpected service error occurred";
        String causeMessage = (ex.getCause() != null && ex.getCause().getMessage() != null) ? ex.getCause().getMessage() : "none";

        // Handle Google AI High Demand (503) specifically
        if (message.contains("high demand") || causeMessage.contains("high demand")) {
            return buildErrorResponse(HttpStatus.TOO_MANY_REQUESTS, 
                "The AI engine is currently experiencing high demand. Please try again in a few seconds.", request);
        }

        String traceId = UUID.randomUUID().toString();
        logger.error("❌ [GlobalException] TraceId: {} - {}: {} - Cause: {} - URL: {}", 
            traceId, ex.getClass().getSimpleName(), message, causeMessage, request.getDescription(false));
        logger.error("Full stack trace: ", ex);

        // If it's a known user-facing message (like Quota Exceeded), show it. otherwise generic.
        String displayMessage = (message.contains("Quota") || message.contains("AI Generation failed")) 
            ? message 
            : "Internal server error. Please contact support with Trace ID: " + traceId;

        StandardErrorResponse errorResponse = StandardErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message(displayMessage)
                .path(((ServletWebRequest)request).getRequest().getRequestURI())
                .timestamp(LocalDateTime.now())
                .traceId(traceId)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<StandardErrorResponse> buildErrorResponse(HttpStatus status, String message, WebRequest request) {
        StandardErrorResponse errorResponse = StandardErrorResponse.builder()
                .status(status.value())
                .message(message)
                .path(((ServletWebRequest)request).getRequest().getRequestURI())
                .timestamp(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString())
                .build();
        return new ResponseEntity<>(errorResponse, status);
    }
}
