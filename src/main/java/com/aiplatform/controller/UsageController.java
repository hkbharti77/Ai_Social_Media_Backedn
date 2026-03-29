package com.aiplatform.controller;

import com.aiplatform.model.CreditUsage;
import com.aiplatform.model.User;
import com.aiplatform.repository.CreditUsageRepository;
import com.aiplatform.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/usage")
@RequiredArgsConstructor
public class UsageController {

    private final CreditUsageRepository creditUsageRepository;

    @GetMapping("/history")
    public ResponseEntity<List<CreditUsage>> getUsageHistory() {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        List<CreditUsage> history = creditUsageRepository.findByUserOrderByCreatedAtDesc(user);
        return ResponseEntity.ok(history);
    }
}
