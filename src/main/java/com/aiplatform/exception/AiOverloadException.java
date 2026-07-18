package com.aiplatform.exception;

import lombok.Getter;

/**
 * AiOverloadException - Thrown when the AI orchestration system is at capacity.
 * Maps to HTTP 429 (Too Many Requests).
 */
@Getter
public class AiOverloadException extends RuntimeException {
    private final int retryAfterSeconds;

    public AiOverloadException(String message) {
        this(message, 5); // Default 5 seconds wait
    }

    public AiOverloadException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
