package com.aiplatform.controller;

import com.aiplatform.model.SocialAccount;
import com.aiplatform.model.User;
import com.aiplatform.repository.UserRepository;
import com.aiplatform.security.UserDetailsImpl;
import com.aiplatform.service.SocialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/social")
public class SocialController {

    @Autowired
    private SocialService socialService;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @GetMapping("/connect/facebook")
    public ResponseEntity<String> connectFacebook() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(socialService.getFacebookAuthUrl(userDetails.getId()));
    }

    @GetMapping("/callback/facebook")
    public void facebookCallback(@RequestParam String code, @RequestParam String state, HttpServletResponse response) throws IOException {
        Long userId = Long.parseLong(state);
        User user = userRepository.findById(userId).get();
        socialService.processFacebookCallback(code, userId, user);

        // Check if Instagram was also connected during this OAuth
        boolean igConnected = socialService.getAccountsByUser(user)
                .stream().anyMatch(a -> "INSTAGRAM".equals(a.getPlatform()));

        String redirectUrl = frontendUrl + "/connect?success=true&instagram=" + igConnected;
        response.sendRedirect(redirectUrl);
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<SocialAccount>> getAccounts() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepository.findById(userDetails.getId()).get();
        return ResponseEntity.ok(socialService.getAccountsByUser(user));
    }

    @DeleteMapping("/accounts/{id}")
    public ResponseEntity<?> disconnectAccount(@PathVariable Long id) {
        socialService.deleteAccount(id);
        return ResponseEntity.ok("Account disconnected successfully");
    }
}
