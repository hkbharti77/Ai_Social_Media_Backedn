package com.aiplatform.controller;

import com.aiplatform.dto.AnalyticsDtos.*;
import com.aiplatform.model.User;
import com.aiplatform.service.AnalyticsService;
import com.aiplatform.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    private User currentUser() {
        return SecurityUtils.getCurrentUser()
            .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

    /** GET /api/v1/analytics/overview?from=2026-09-01&to=2026-09-30 */
    @GetMapping("/overview")
    public ResponseEntity<OverviewMetrics> getOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from == null) from = LocalDate.now().minusDays(29);
        if (to   == null) to   = LocalDate.now();
        return ResponseEntity.ok(analyticsService.getOverviewMetrics(currentUser(), from, to));
    }

    /** GET /api/v1/analytics/posts?from=&to=&platform=&sort=&page=&size= */
    @GetMapping("/posts")
    public ResponseEntity<Page<PostPerformanceDto>> getPostPerformance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String platform,
            @RequestParam(defaultValue = "publishedAt") String sort,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        if (from == null) from = LocalDate.now().minusDays(29);
        if (to   == null) to   = LocalDate.now();
        return ResponseEntity.ok(analyticsService.getPostPerformance(
            currentUser(), PageRequest.of(page, size), platform, from, to, sort));
    }

    /** GET /api/v1/analytics/platforms?from=&to= */
    @GetMapping("/platforms")
    public ResponseEntity<List<PlatformBreakdownDto>> getPlatformBreakdown(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from == null) from = LocalDate.now().minusDays(29);
        if (to   == null) to   = LocalDate.now();
        return ResponseEntity.ok(analyticsService.getPlatformBreakdown(currentUser(), from, to));
    }

    /** GET /api/v1/analytics/content-types?from=&to= */
    @GetMapping("/content-types")
    public ResponseEntity<ContentTypeBreakdownDto> getContentTypes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from == null) from = LocalDate.now().minusDays(29);
        if (to   == null) to   = LocalDate.now();
        return ResponseEntity.ok(analyticsService.getContentTypeBreakdown(currentUser(), from, to));
    }

    /** GET /api/v1/analytics/heatmap */
    @GetMapping("/heatmap")
    public ResponseEntity<List<HeatmapDayDto>> getHeatmap() {
        return ResponseEntity.ok(analyticsService.getHeatmapData(currentUser()));
    }

    /** GET /api/v1/analytics/credits?days=30 */
    @GetMapping("/credits")
    public ResponseEntity<CreditUsageTimelineDto> getCreditUsage(
            @RequestParam(defaultValue = "30") int days) {
        if (days < 1) days = 7;
        if (days > 365) days = 365;
        return ResponseEntity.ok(analyticsService.getCreditUsageTimeline(currentUser(), days));
    }

    /** GET /api/v1/analytics/top-posts?limit=5 */
    @GetMapping("/top-posts")
    public ResponseEntity<List<PostPerformanceDto>> getTopPosts(
            @RequestParam(defaultValue = "5") int limit) {
        if (limit < 1) limit = 5;
        if (limit > 20) limit = 20;
        return ResponseEntity.ok(analyticsService.getTopPosts(currentUser(), limit));
    }
}
