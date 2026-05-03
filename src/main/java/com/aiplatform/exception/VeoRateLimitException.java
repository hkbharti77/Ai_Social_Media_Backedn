package com.aiplatform.exception;

public class VeoRateLimitException extends RuntimeException {
    public VeoRateLimitException(String message) {
        super(message);
    }
}
