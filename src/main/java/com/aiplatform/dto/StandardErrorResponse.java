package com.aiplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StandardErrorResponse {
    private int status;
    private String message;
    private String path;
    private LocalDateTime timestamp;
    private String traceId; // Useful for enterprise log correlation
}
