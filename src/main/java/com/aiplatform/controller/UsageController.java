package com.aiplatform.controller;

import com.aiplatform.model.AiUsageLog;
import com.aiplatform.model.CreditUsage;
import com.aiplatform.model.User;
import com.aiplatform.repository.AiUsageLogRepository;
import com.aiplatform.repository.CreditUsageRepository;
import com.aiplatform.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/usage")
@RequiredArgsConstructor
public class UsageController {

    private final CreditUsageRepository creditUsageRepository;
    private final AiUsageLogRepository aiUsageLogRepository;

    @GetMapping("/history")
    public ResponseEntity<List<CreditUsage>> getUsageHistory() {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        List<CreditUsage> history = creditUsageRepository.findByUserOrderByCreatedAtDesc(user);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/ai-logs")
    public ResponseEntity<List<AiUsageLog>> getAiUsageLogs() {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        List<AiUsageLog> logs = aiUsageLogRepository.findByUserOrderByCreatedAtDesc(user);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/ai-summary")
    public ResponseEntity<List<Map<String, Object>>> getAiUsageSummary() {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        // Default to last 30 days
        LocalDateTime startDate = LocalDateTime.now().minusDays(30);
        List<Map<String, Object>> summary = aiUsageLogRepository.getUsageSummaryByUser(user, startDate);
        return ResponseEntity.ok(summary);
    }
}
