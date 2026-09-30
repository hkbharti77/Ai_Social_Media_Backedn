package com.aiplatform.service;

import com.aiplatform.dto.AnalyticsDtos.*;
import com.aiplatform.model.Post;
import com.aiplatform.model.PostStatus;
import com.aiplatform.model.SubscriptionTier;
import com.aiplatform.model.User;
import com.aiplatform.repository.AiUsageLogRepository;
import com.aiplatform.repository.CreditUsageRepository;
import com.aiplatform.repository.PostRepository;
import com.aiplatform.repository.SocialAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final PostRepository postRepository;
    private final CreditUsageRepository creditUsageRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final SocialAccountRepository socialAccountRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ─── Overview Metrics ──────────────────────────────────────────────────────

    public OverviewMetrics getOverviewMetrics(User user, LocalDate from, LocalDate to) {
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.plusDays(1).atStartOfDay();

        // Previous period of same length
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDateTime prevFromDt = fromDt.minusDays(days);
        LocalDateTime prevToDt   = fromDt;

        List<Post> allPosts = postRepository.findByUser(user);

        List<Post> currentPublished = allPosts.stream()
            .filter(p -> p.getStatus() == PostStatus.PUBLISHED)
            .filter(p -> p.getPublishedAt() != null
                && !p.getPublishedAt().isBefore(fromDt)
                && p.getPublishedAt().isBefore(toDt))
            .toList();

        List<Post> prevPublished = allPosts.stream()
            .filter(p -> p.getStatus() == PostStatus.PUBLISHED)
            .filter(p -> p.getPublishedAt() != null
                && !p.getPublishedAt().isBefore(prevFromDt)
                && p.getPublishedAt().isBefore(prevToDt))
            .toList();

        long curCount  = currentPublished.size();
        long prevCount = prevPublished.size();
        double publishedChange = prevCount == 0 ? 0 :
            Math.round(((double)(curCount - prevCount) / prevCount) * 1000d) / 10d;

        long curEngagement = currentPublished.stream()
            .mapToLong(p -> safe(p.getLikes()) + safe(p.getCommentsCount()) + safe(p.getShares()))
            .sum();
        long prevEngagement = prevPublished.stream()
            .mapToLong(p -> safe(p.getLikes()) + safe(p.getCommentsCount()) + safe(p.getShares()))
            .sum();
        double engagementChange = prevEngagement == 0 ? 0 :
            Math.round(((double)(curEngagement - prevEngagement) / prevEngagement) * 1000d) / 10d;

        double avgEngRate = curCount == 0 ? 0 :
            Math.round((double) curEngagement / curCount * 10d) / 10d;

        // Credits used in period
        var creditUsages = creditUsageRepository.findByUserAndCreatedAtAfter(user, fromDt);
        double creditsUsed = creditUsages.stream().mapToDouble(c -> c.getAmount() != null ? c.getAmount() : 0).sum();
        double totalCredits = (user.getMonthlyCredits() != null ? user.getMonthlyCredits() : 0)
            + (user.getBonusCredits() != null ? user.getBonusCredits() : 0);

        // AI generations in period
        long aiGens = aiUsageLogRepository.findByUserOrderByCreatedAtDesc(user).stream()
            .filter(l -> l.getCreatedAt() != null && !l.getCreatedAt().isBefore(fromDt) && l.getCreatedAt().isBefore(toDt))
            .count();

        int connectedPlatforms = socialAccountRepository.findByUser(user).size();

        // Days left in billing cycle — assume monthly reset from lastResetAt
        int daysLeft = 30;
        if (user.getLastResetAt() != null) {
            LocalDateTime nextReset = user.getLastResetAt().plusMonths(1);
            daysLeft = (int) Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), nextReset.toLocalDate()));
        }

        return new OverviewMetrics(
            curCount, prevCount, publishedChange,
            curEngagement, prevEngagement, engagementChange,
            avgEngRate,
            Math.round(creditsUsed * 10d) / 10d,
            Math.round(totalCredits * 10d) / 10d,
            aiGens,
            connectedPlatforms,
            daysLeft
        );
    }

    // ─── Post Performance ──────────────────────────────────────────────────────

    public Page<PostPerformanceDto> getPostPerformance(
            User user, Pageable pageable,
            String platform, LocalDate from, LocalDate to, String sortBy) {

        requireTier(user, 1, "Post performance analytics requires STANDARD plan or above");

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.plusDays(1).atStartOfDay();

        List<Post> posts = postRepository.findByUser(user).stream()
            .filter(p -> p.getStatus() == PostStatus.PUBLISHED)
            .filter(p -> p.getPublishedAt() != null
                && !p.getPublishedAt().isBefore(fromDt)
                && p.getPublishedAt().isBefore(toDt))
            .filter(p -> platform == null || platform.isBlank() || platform.equalsIgnoreCase(p.getPlatform()))
            .toList();

        List<PostPerformanceDto> dtos = posts.stream()
            .map(this::toPerformanceDto)
            .sorted(getPerformanceSorter(sortBy))
            .toList();

        int start = (int) pageable.getOffset();
        int end   = Math.min(start + pageable.getPageSize(), dtos.size());
        List<PostPerformanceDto> page = start > dtos.size() ? List.of() : dtos.subList(start, end);

        return new PageImpl<>(page, pageable, dtos.size());
    }

    // ─── Platform Breakdown ────────────────────────────────────────────────────

    public List<PlatformBreakdownDto> getPlatformBreakdown(User user, LocalDate from, LocalDate to) {
        requireTier(user, 2, "Platform breakdown analytics requires PRO plan or above");

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.plusDays(1).atStartOfDay();

        List<Post> published = postRepository.findByUser(user).stream()
            .filter(p -> p.getStatus() == PostStatus.PUBLISHED)
            .filter(p -> p.getPublishedAt() != null
                && !p.getPublishedAt().isBefore(fromDt)
                && p.getPublishedAt().isBefore(toDt))
            .toList();

        Map<String, List<Post>> byPlatform = published.stream()
            .collect(Collectors.groupingBy(p -> p.getPlatform() != null ? p.getPlatform() : "UNKNOWN"));

        return byPlatform.entrySet().stream()
            .map(e -> {
                List<Post> ps = e.getValue();
                long totalEng = ps.stream()
                    .mapToLong(p -> safe(p.getLikes()) + safe(p.getCommentsCount()) + safe(p.getShares()))
                    .sum();
                double avgEng = ps.isEmpty() ? 0 : Math.round((double) totalEng / ps.size() * 10d) / 10d;
                long totalReach = ps.stream().mapToLong(p -> safe(p.getReach())).sum();
                long totalLikes = ps.stream().mapToLong(p -> safe(p.getLikes())).sum();
                long totalShares = ps.stream().mapToLong(p -> safe(p.getShares())).sum();
                return new PlatformBreakdownDto(e.getKey(), ps.size(), avgEng, totalReach, totalLikes, totalShares);
            })
            .sorted(Comparator.comparingLong(PlatformBreakdownDto::postsCount).reversed())
            .toList();
    }

    // ─── Content Type Breakdown ────────────────────────────────────────────────

    public ContentTypeBreakdownDto getContentTypeBreakdown(User user, LocalDate from, LocalDate to) {
        requireTier(user, 1, "Content type analytics requires STANDARD plan or above");

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.plusDays(1).atStartOfDay();

        List<Post> published = postRepository.findByUser(user).stream()
            .filter(p -> p.getStatus() == PostStatus.PUBLISHED)
            .filter(p -> p.getPublishedAt() != null
                && !p.getPublishedAt().isBefore(fromDt)
                && p.getPublishedAt().isBefore(toDt))
            .toList();

        Map<String, List<Post>> byType = published.stream()
            .collect(Collectors.groupingBy(this::resolveContentType));

        List<ContentTypeSlice> slices = byType.entrySet().stream()
            .map(e -> {
                List<Post> ps = e.getValue();
                long totalEng = ps.stream()
                    .mapToLong(p -> safe(p.getLikes()) + safe(p.getCommentsCount()) + safe(p.getShares()))
                    .sum();
                double avg = ps.isEmpty() ? 0 : Math.round((double) totalEng / ps.size() * 10d) / 10d;
                return new ContentTypeSlice(e.getKey(), ps.size(), avg);
            })
            .sorted(Comparator.comparingLong(ContentTypeSlice::count).reversed())
            .toList();

        return new ContentTypeBreakdownDto(slices);
    }

    // ─── Heatmap ───────────────────────────────────────────────────────────────

    public List<HeatmapDayDto> getHeatmapData(User user) {
        // FREE: last 7 days only. Others: full 52 weeks
        int lookbackDays = user.getSubscriptionTier().getLevel() == 0 ? 7 : 365;
        LocalDate startDate = LocalDate.now().minusDays(lookbackDays - 1);

        List<Post> posts = postRepository.findByUser(user).stream()
            .filter(p -> {
                LocalDateTime dt = p.getPublishedAt() != null ? p.getPublishedAt() : p.getScheduledAt();
                return dt != null && !dt.toLocalDate().isBefore(startDate);
            })
            .toList();

        // Build a map: date → {platform → count}
        Map<LocalDate, Map<String, Integer>> dateMap = new LinkedHashMap<>();
        for (Post p : posts) {
            LocalDateTime dt = p.getPublishedAt() != null ? p.getPublishedAt() : p.getScheduledAt();
            if (dt == null) continue;
            LocalDate d = dt.toLocalDate();
            String platform = p.getPlatform() != null ? p.getPlatform() : "UNKNOWN";
            dateMap.computeIfAbsent(d, k -> new HashMap<>())
                   .merge(platform, 1, Integer::sum);
        }

        // Fill all days in range with 0 if missing
        List<HeatmapDayDto> result = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(LocalDate.now()); d = d.plusDays(1)) {
            Map<String, Integer> platformMap = dateMap.getOrDefault(d, Map.of());
            int total = platformMap.values().stream().mapToInt(Integer::intValue).sum();
            result.add(new HeatmapDayDto(d.format(DATE_FMT), total, platformMap));
        }
        return result;
    }

    // ─── Credit Usage Timeline ─────────────────────────────────────────────────

    public CreditUsageTimelineDto getCreditUsageTimeline(User user, int days) {
        requireTier(user, 1, "Credit usage analytics requires CREATOR plan or above");

        LocalDateTime since = LocalDateTime.now().minusDays(days);
        var usages = creditUsageRepository.findByUserAndCreatedAtAfter(user, since);

        // Daily totals
        Map<LocalDate, Double> dailyMap = new LinkedHashMap<>();
        LocalDate startDate = LocalDate.now().minusDays(days - 1);
        for (LocalDate d = startDate; !d.isAfter(LocalDate.now()); d = d.plusDays(1)) {
            dailyMap.put(d, 0.0);
        }
        for (var u : usages) {
            if (u.getCreatedAt() != null) {
                dailyMap.merge(u.getCreatedAt().toLocalDate(),
                    u.getAmount() != null ? u.getAmount() : 0.0, Double::sum);
            }
        }
        List<DailyCredit> daily = dailyMap.entrySet().stream()
            .map(e -> new DailyCredit(e.getKey().format(DATE_FMT),
                Math.round(e.getValue() * 10d) / 10d))
            .toList();

        double totalUsed = usages.stream()
            .mapToDouble(u -> u.getAmount() != null ? u.getAmount() : 0).sum();
        double totalBudget = (user.getMonthlyCredits() != null ? user.getMonthlyCredits() : 0)
            + (user.getBonusCredits() != null ? user.getBonusCredits() : 0);

        // By action type
        Map<String, long[]> actionMap = new HashMap<>(); // [count, sumX10]
        for (var u : usages) {
            String purpose = u.getPurpose() != null ? u.getPurpose() : "Other";
            actionMap.computeIfAbsent(purpose, k -> new long[]{0, 0});
            actionMap.get(purpose)[0]++;
            actionMap.get(purpose)[1] += Math.round((u.getAmount() != null ? u.getAmount() : 0) * 10d);
        }
        List<CreditByAction> byAction = actionMap.entrySet().stream()
            .map(e -> new CreditByAction(e.getKey(), e.getValue()[0], e.getValue()[1] / 10d))
            .sorted(Comparator.comparingDouble(CreditByAction::total).reversed())
            .toList();

        return new CreditUsageTimelineDto(daily, Math.round(totalUsed * 10d) / 10d, totalBudget, byAction);
    }

    // ─── Top Posts ─────────────────────────────────────────────────────────────

    public List<PostPerformanceDto> getTopPosts(User user, int limit) {
        return postRepository.findByUser(user).stream()
            .filter(p -> p.getStatus() == PostStatus.PUBLISHED)
            .map(this::toPerformanceDto)
            .sorted(Comparator.comparingDouble(dto ->
                -((PostPerformanceDto) dto).likes()
                - ((PostPerformanceDto) dto).commentsCount() * 3.0
                - ((PostPerformanceDto) dto).shares() * 5.0))
            .limit(limit)
            .toList();
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private void requireTier(User user, int minLevel, String message) {
        if (user.getSubscriptionTier().getLevel() < minLevel) {
            throw new RuntimeException(message);
        }
    }

    private long safe(Long v) { return v != null ? v : 0L; }

    private String resolveContentType(Post p) {
        if (Boolean.TRUE.equals(p.getIsReel())) return "Reel";
        if (Boolean.TRUE.equals(p.getIsCarousel())) return "Carousel";
        if (Boolean.TRUE.equals(p.getIsStory())) return "Story";
        if (Boolean.TRUE.equals(p.getIsPoll())) return "Poll";
        if (Boolean.TRUE.equals(p.getIsThread())) return "Thread";
        if (p.getImageUrl() != null && !p.getImageUrl().isBlank()) return "Image";
        return "Text";
    }

    private PostPerformanceDto toPerformanceDto(Post p) {
        long eng = safe(p.getLikes()) + safe(p.getCommentsCount()) + safe(p.getShares());
        double engRate = safe(p.getReach()) > 0
            ? Math.round((double) eng / p.getReach() * 1000d) / 10d : 0;
        String publishedAt = p.getPublishedAt() != null
            ? p.getPublishedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null;
        String caption = p.getCaption() != null
            ? (p.getCaption().length() > 120 ? p.getCaption().substring(0, 120) + "…" : p.getCaption())
            : "";
        return new PostPerformanceDto(
            p.getId(), caption, p.getImageUrl(), p.getVideoUrl(),
            p.getPlatform(), publishedAt,
            safe(p.getLikes()), safe(p.getCommentsCount()), safe(p.getShares()), safe(p.getReach()),
            engRate, resolveContentType(p), p.getStatus().name()
        );
    }

    private Comparator<PostPerformanceDto> getPerformanceSorter(String sortBy) {
        if (sortBy == null) sortBy = "publishedAt";
        return switch (sortBy) {
            case "likes"          -> Comparator.comparingLong(PostPerformanceDto::likes).reversed();
            case "shares"         -> Comparator.comparingLong(PostPerformanceDto::shares).reversed();
            case "engagementRate" -> Comparator.comparingDouble(PostPerformanceDto::engagementRate).reversed();
            default               -> Comparator.comparing(
                (PostPerformanceDto d) -> d.publishedAt() != null ? d.publishedAt() : "",
                Comparator.reverseOrder());
        };
    }
}
