package com.aiplatform.controller;

import com.aiplatform.model.SocialAccount;
import com.aiplatform.model.User;
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

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @GetMapping("/connect/facebook")
    public ResponseEntity<String> connectFacebook() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("Authenticated user not found");
        }
        String stateToken = jwtUtils.generateStateToken(userId);
        return ResponseEntity.ok(socialService.getFacebookAuthUrl(stateToken));
    }

    @GetMapping("/callback/facebook")
    public void facebookCallback(@RequestParam String code, @RequestParam String state, HttpServletResponse response) throws IOException {
        Long userId = jwtUtils.getUserIdFromStateToken(state);
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("User context lost during callback"));
        
        socialService.processFacebookCallback(code, userId, user);

        // Check if Instagram was also connected during this OAuth
        boolean igConnected = socialService.getAccountsByUser(user)
                .stream().anyMatch(a -> "INSTAGRAM".equals(a.getPlatform()));

        String redirectUrl = frontendUrl + "/connect?success=true&instagram=" + igConnected;
        response.sendRedirect(redirectUrl);
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<SocialAccount>> getAccounts() {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        return ResponseEntity.ok(socialService.getAccountsByUser(user));
    }

    @DeleteMapping("/accounts/{id}")
    public ResponseEntity<?> disconnectAccount(@PathVariable Long id) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        socialService.deleteAccount(id, user);
        return ResponseEntity.ok("Account disconnected successfully");
    }
}
