package com.aiplatform.controller;

import com.aiplatform.model.User;
import com.aiplatform.service.InstagramInsightsService;
import com.aiplatform.util.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/insights")
@RequiredArgsConstructor
public class InsightsController {

    private final InstagramInsightsService insightsService;

    @GetMapping("/best-time")
    public ResponseEntity<JsonNode> getBestTime() {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        return ResponseEntity.ok(insightsService.getBestTimeReport(user));
    }
}
