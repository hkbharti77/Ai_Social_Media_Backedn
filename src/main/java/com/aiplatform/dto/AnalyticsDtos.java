package com.aiplatform.dto;

import java.util.List;
import java.util.Map;

public class AnalyticsDtos {

    // ─── Overview Panel ────────────────────────────────────────────────────────

    public record OverviewMetrics(
        long publishedThisMonth,
        long publishedLastMonth,
        double publishedChangePercent,
        long totalEngagementThisMonth,
        long totalEngagementLastMonth,
        double engagementChangePercent,
        double avgEngagementRate,
        double creditsUsedThisMonth,
        double totalCredits,
        long aiGenerationsThisMonth,
        int connectedPlatforms,
        int daysLeftInCycle
    ) {}

    // ─── Post Performance Table ────────────────────────────────────────────────

    public record PostPerformanceDto(
        long id,
        String caption,
        String imageUrl,
        String videoUrl,
        String platform,
        String publishedAt,
        long likes,
        long commentsCount,
        long shares,
        long reach,
        double engagementRate,
        String contentType,
        String status
    ) {}

    // ─── Platform Breakdown ────────────────────────────────────────────────────

    public record PlatformBreakdownDto(
        String platform,
        long postsCount,
        double avgEngagement,
        long totalReach,
        long totalLikes,
        long totalShares
    ) {}

    // ─── Content Type Breakdown ────────────────────────────────────────────────

    public record ContentTypeSlice(
        String type,
        long count,
        double avgEngagement
    ) {}

    public record ContentTypeBreakdownDto(
        List<ContentTypeSlice> distribution
    ) {}

    // ─── Heatmap ───────────────────────────────────────────────────────────────

    public record HeatmapDayDto(
        String date,
        int postCount,
        Map<String, Integer> byPlatform
    ) {}

    // ─── Credit Usage Timeline ─────────────────────────────────────────────────

    public record DailyCredit(
        String date,
        double creditsUsed
    ) {}

    public record CreditByAction(
        String actionType,
        long count,
        double total
    ) {}

    public record CreditUsageTimelineDto(
        List<DailyCredit> daily,
        double totalUsed,
        double totalBudget,
        List<CreditByAction> byAction
    ) {}
}
