package com.aiplatform.controller;

import com.aiplatform.model.CreditUsage;
import com.aiplatform.model.User;
import com.aiplatform.repository.CreditUsageRepository;
import com.aiplatform.repository.UserRepository;
import com.aiplatform.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/usage")
@RequiredArgsConstructor
public class UsageController {

    private final CreditUsageRepository creditUsageRepository;
    private final UserRepository userRepository;

    @GetMapping("/history")
    public ResponseEntity<List<CreditUsage>> getUsageHistory(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        User user = userRepository.findByEmail(userDetails.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        List<CreditUsage> history = creditUsageRepository.findByUserOrderByCreatedAtDesc(user);
        return ResponseEntity.ok(history);
    }
}
