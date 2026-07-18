package com.aiplatform.scheduler;

import com.aiplatform.model.Post;
import com.aiplatform.model.PostStatus;
import com.aiplatform.model.User;
import com.aiplatform.repository.CreditUsageRepository;
import com.aiplatform.repository.PostRepository;
import com.aiplatform.repository.UserRepository;
import com.aiplatform.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class WeeklyReportTask {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CreditUsageRepository creditUsageRepository;
    private final EmailService emailService;

    /**
     * Runs every Sunday at 9:00 AM IST (03:30 UTC) to send weekly activity reports.
     */
    @Scheduled(cron = "0 30 3 * * SUN") // 9:00 AM IST = 03:30 UTC, every Sunday
    public void sendWeeklyReports() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekStart = now.minusDays(7);

        String weekRange = weekStart.format(DateTimeFormatter.ofPattern("dd MMM")) +
                " – " + now.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));

        log.info("📊 [WeeklyReport] Generating weekly reports for week: {}", weekRange);

        List<User> allUsers = userRepository.findAll();
        int sent = 0;

        for (User user : allUsers) {
            try {
                // Posts published this week
                List<Post> publishedPosts = postRepository.findByUserAndStatusAndCreatedAtAfter(
                        user, PostStatus.PUBLISHED, weekStart);
                int postsPublished = publishedPosts.size();

                // Posts failed this week
                List<Post> failedPosts = postRepository.findByUserAndStatusAndCreatedAtAfter(
                        user, PostStatus.FAILED, weekStart);
                int postsFailed = failedPosts.size();

                // Credits used this week (sum of negative amounts = deductions)
                double creditsUsed = creditUsageRepository
                        .findByUserAndCreatedAtAfter(user, weekStart)
                        .stream()
                        .filter(cu -> cu.getAmount() < 0)
                        .mapToDouble(cu -> Math.abs(cu.getAmount()))
                        .sum();

                double creditsRemaining = user.getMonthlyCredits() != null ? user.getMonthlyCredits() : 0.0;

                // Only send if user had any activity OR has credits
                if (postsPublished > 0 || postsFailed > 0 || creditsUsed > 0 || creditsRemaining > 0) {
                    emailService.sendWeeklyReportEmail(
                            user, postsPublished, postsFailed,
                            creditsUsed, creditsRemaining, weekRange);
                    sent++;
                }
            } catch (Exception e) {
                log.warn("⚠️ [WeeklyReport] Failed to send report for user {}: {}", user.getEmail(), e.getMessage());
            }
        }

        log.info("✅ [WeeklyReport] Sent {} weekly reports.", sent);
    }
}
