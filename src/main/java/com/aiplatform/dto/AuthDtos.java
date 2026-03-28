package com.aiplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;
import java.util.Set;

public class AuthDtos {

    @Data
    public static class LoginRequest {
        private String email;
        private String password;
    }

    @Data
    public static class SignupRequest {
        private String email;
        private String fullName;
        private String password;
        private Set<String> roles;
    }

    @Data
    @AllArgsConstructor
    public static class JwtResponse {
        private String token;
        private String refreshToken;
        private Long id;
        private String email;
        private String fullName;
        private List<String> roles;
    }

    @Data
    public static class TokenRefreshRequest {
        private String refreshToken;
    }

    @Data
    @AllArgsConstructor
    public static class TokenRefreshResponse {
        private String accessToken;
        private String refreshToken;
    }

    @Data
    @AllArgsConstructor
    public static class MessageResponse {
        private String message;
    }
}
