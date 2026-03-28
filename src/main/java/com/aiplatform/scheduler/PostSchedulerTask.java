package com.aiplatform.scheduler;

import com.aiplatform.model.Post;
import com.aiplatform.model.PostStatus;
import com.aiplatform.repository.PostRepository;
import com.aiplatform.service.AutoPostService;
import com.aiplatform.service.PublisherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Component
public class PostSchedulerTask {
    private static final Logger logger = LoggerFactory.getLogger(PostSchedulerTask.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PublisherService publisherService;

    @Autowired
    private AutoPostService autoPostService;

    @Value("${app.scheduling.morning-publish-time:09:00}")
    private String morningPublishTime;

    @Value("${app.scheduling.evening-publish-time:20:00}")
    private String eveningPublishTime;

    // ─────────────────────────────────────────────────
    // EXISTING: Publish SCHEDULED posts every minute
    // ─────────────────────────────────────────────────

    @Scheduled(fixedRate = 60000) // Every minute
    public void processScheduledPosts() {
        LocalDateTime now = LocalDateTime.now(IST);
        logger.info("⏰ [Scheduler] Initiating check for due posts at {} IST", now);

        try {
            List<Post> duePosts = postRepository.findByStatusAndScheduledAtBefore(
                    PostStatus.SCHEDULED, now);

            if (duePosts.isEmpty()) {
                logger.debug("✅ [Scheduler] No posts due for transmission at this time.");
                return;
            }

            logger.info("🚀 [Scheduler] Found {} posts due for transmission. Dispatching...", duePosts.size());

            for (Post post : duePosts) {
                try {
                    logger.info("📡 [Dispatcher] Handing off post {} to PublisherService", post.getId());
                    publisherService.publishPost(post);
                } catch (Exception e) {
                    logger.error("❌ [Dispatcher] Critical failure while handing off post {}: {}", post.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            logger.error("🚫 [Scheduler] Fatal database error during scheduled post check: {}", e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────
    // NEW: Auto Draft Generation (IST)
    // ─────────────────────────────────────────────────

    /**
     * Morning Draft Generation (defaults to 6:00 AM IST)
     */
    @Scheduled(cron = "${app.scheduling.morning-draft-cron}", zone = "Asia/Kolkata")
    public void generateMorningDraft() {
        logger.info("🌅 [AutoPost Scheduler] Triggering MORNING draft generation at {}", LocalDateTime.now(IST));
        try {
            autoPostService.generateDraftsForAllUsers("MORNING");
        } catch (Exception e) {
            logger.error("❌ [AutoPost Scheduler] Morning draft generation failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Evening Draft Generation (defaults to 3:00 PM IST)
     */
    @Scheduled(cron = "${app.scheduling.evening-draft-cron}", zone = "Asia/Kolkata")
    public void generateEveningDraft() {
        logger.info("🌆 [Scheduler] Triggering EVENING draft generation at {}", LocalDateTime.now(IST));
        try {
            autoPostService.generateDraftsForAllUsers("EVENING");
        } catch (Exception e) {
            logger.error("❌ [AutoPost Scheduler] Evening draft generation failed: {}", e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────
    // NEW: Auto-Schedule Unapproved Drafts (IST)
    // ─────────────────────────────────────────────────

    /**
     * Auto-Schedule Morning Drafts (defaults to 8:00 AM IST)
     */
    @Scheduled(cron = "${app.scheduling.morning-auto-cron}", zone = "Asia/Kolkata")
    public void autoScheduleMorning() {
        logger.info("🤖 [Scheduler] Auto-scheduling unapproved MORNING drafts at {}", LocalDateTime.now(IST));
        try {
            // Morning drafts auto-publish at target time (e.g. 9:00 AM) today IST
            LocalTime time = LocalTime.parse(morningPublishTime);
            LocalDateTime publishAt = LocalDate.now(IST).atTime(time);
            autoPostService.autoScheduleIfNotApproved("MORNING", publishAt);
        } catch (Exception e) {
            logger.error("❌ [AutoPost Scheduler] Morning auto-schedule check failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Auto-Schedule Evening Drafts (defaults to 6:00 PM IST)
     */
    @Scheduled(cron = "${app.scheduling.evening-auto-cron}", zone = "Asia/Kolkata")
    public void autoScheduleEveningIfNotApproved() {
        logger.info("🤖 [Scheduler] Auto-scheduling unapproved EVENING drafts at {}", LocalDateTime.now(IST));
        try {
            // Evening drafts auto-publish at target time (e.g. 8:00 PM) today IST
            LocalTime time = LocalTime.parse(eveningPublishTime);
            LocalDateTime publishAt = LocalDate.now(IST).atTime(time);
            autoPostService.autoScheduleIfNotApproved("EVENING", publishAt);
        } catch (Exception e) {
            logger.error("❌ [AutoPost Scheduler] Evening auto-schedule check failed: {}", e.getMessage(), e);
        }
    }
}
