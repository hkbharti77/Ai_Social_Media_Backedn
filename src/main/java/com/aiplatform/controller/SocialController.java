package com.aiplatform.controller;

import com.aiplatform.dto.SocialTokenRequest;
import com.aiplatform.model.SocialAccount;
import com.aiplatform.model.User;
import com.aiplatform.repository.UserRepository;
import com.aiplatform.security.JwtUtils;
import com.aiplatform.service.SocialService;
import com.aiplatform.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/social")
@RequiredArgsConstructor
public class SocialController {

    private final SocialService socialService;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @GetMapping("/connect/linkedin")
    public ResponseEntity<String> connectLinkedIn() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("Authenticated user not found");
        }
        String stateToken = jwtUtils.generateStateToken(userId);
        return ResponseEntity.ok(socialService.getLinkedInAuthUrl(stateToken));
    }

    @GetMapping("/callback/facebook")
    public void facebookCallback(@RequestParam String code, @RequestParam String state, HttpServletResponse response) throws IOException {
        Long userId = jwtUtils.getUserIdFromStateToken(state);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User context lost during callback - Invalid userId: " + userId));
        
        socialService.processFacebookCallback(code, userId, user);

        // Check if Instagram was also connected during this OAuth
        boolean igConnected = socialService.getAccountsByUser(user)
                .stream().anyMatch(a -> "INSTAGRAM".equals(a.getPlatform()));

        String redirectUrl = frontendUrl + "/connect?success=true&instagram=" + igConnected;
        response.sendRedirect(redirectUrl);
    }

    @GetMapping("/callback/linkedin")
    public void linkedinCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String error_description,
            @RequestParam String state,
            HttpServletResponse response) throws IOException {
        
        if (error != null) {
            String redirectUrl = frontendUrl + "/connect?error=" + java.net.URLEncoder.encode(error_description != null ? error_description : error, "UTF-8");
            response.sendRedirect(redirectUrl);
            return;
        }

        if (code == null) {
            response.sendRedirect(frontendUrl + "/connect?error=Missing+authorization+code");
            return;
        }

        Long userId = jwtUtils.getUserIdFromStateToken(state);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User context lost during callback - Invalid userId: " + userId));
        
        socialService.processLinkedInCallback(code, user);

        String redirectUrl = frontendUrl + "/connect?success=true&platform=linkedin";
        response.sendRedirect(redirectUrl);
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<SocialAccount>> getAccounts() {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        return ResponseEntity.ok(socialService.getAccountsByUser(user));
    }

    @PostMapping("/connect/token")
    public ResponseEntity<String> connectWithToken(@RequestBody SocialTokenRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        socialService.connectWithUserToken(request.getAccessToken(), user);
        return ResponseEntity.ok("Accounts connected successfully via token");
    }

    @PostMapping("/connect/default")
    public ResponseEntity<String> connectWithDefaultToken() {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        socialService.connectWithConfiguredToken(user);
        return ResponseEntity.ok("Accounts connected successfully via configured token");
    }

    @DeleteMapping("/accounts/{id}")
    public ResponseEntity<?> disconnectAccount(@PathVariable Long id) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        socialService.deleteAccount(id, user);
        return ResponseEntity.ok("Account disconnected successfully");
    }
}
