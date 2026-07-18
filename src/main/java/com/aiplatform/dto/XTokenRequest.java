package com.aiplatform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for manually connecting an X (Twitter) account using tokens
 * generated from the X Developer Portal OAuth 2.0 flow.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class XTokenRequest {

    @NotBlank(message = "Access token is required")
    private String accessToken;

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
