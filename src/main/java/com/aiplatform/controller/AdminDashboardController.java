package com.aiplatform.controller;

import com.aiplatform.dto.AdminAnalyticsDtos.*;
import com.aiplatform.model.AiUsageLog;
import com.aiplatform.service.AdminAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminAnalyticsService adminAnalyticsService;
    private final com.aiplatform.service.OwnerSecurityService ownerSecurityService;

    private void validateOwner() {
        com.aiplatform.model.User user = com.aiplatform.util.SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        if (!ownerSecurityService.isOwner(user.getEmail())) {
            throw new org.springframework.security.access.AccessDeniedException("Strict Owner Access Only");
        }
    }

    @GetMapping("/stats/overview")
    public ResponseEntity<AdminDashboardStats> getOverview() {
        validateOwner();
        return ResponseEntity.ok(adminAnalyticsService.getOverviewStats());
    }

    @GetMapping("/analytics/models")
    public ResponseEntity<List<ModelUsageStats>> getModelStats() {
        validateOwner();
        return ResponseEntity.ok(adminAnalyticsService.getModelDetailedStats());
    }

    @GetMapping("/analytics/user-intelligence")
    public ResponseEntity<List<UserIntelligenceDto>> getUserIntelligence() {
        validateOwner();
        return ResponseEntity.ok(adminAnalyticsService.getUserIntelligence());
    }

    @GetMapping("/analytics/users-directory")
    public ResponseEntity<Page<UserIntelligenceDto>> getUserDirectory(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        validateOwner();
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        return ResponseEntity.ok(adminAnalyticsService.getUserDirectoryIntelligence(query, pageable));
    }

    @GetMapping("/logs/ai-detailed")
    public ResponseEntity<Page<AiUsageLog>> getDetailedLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminAnalyticsService.getAuditLogs(page, size));
    }
}
