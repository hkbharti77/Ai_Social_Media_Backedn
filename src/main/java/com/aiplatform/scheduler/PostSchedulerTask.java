package com.aiplatform.scheduler;

import com.aiplatform.model.BusinessProfile;
import com.aiplatform.model.Post;
import com.aiplatform.model.PostStatus;
import com.aiplatform.repository.BusinessProfileRepository;
import com.aiplatform.repository.PostRepository;
import com.aiplatform.service.AutoPostService;
import com.aiplatform.service.EvergreenService;
import com.aiplatform.service.PublisherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.util.List;

@Component
public class PostSchedulerTask {
    private static final Logger logger = LoggerFactory.getLogger(PostSchedulerTask.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private BusinessProfileRepository businessProfileRepository;

    @Autowired
    private PublisherService publisherService;

    @Autowired
    private AutoPostService autoPostService;

    @Autowired
    private EvergreenService evergreenService;

    // ─────────────────────────────────────────────────
    // EXISTING: Publish SCHEDULED posts every minute
    // ─────────────────────────────────────────────────

    @Scheduled(fixedRate = 60000) // Every minute
    public void processScheduledPosts() {
        LocalDateTime now = LocalDateTime.now(IST);
        logger.info("[Scheduler] Initiating check for due posts at {} IST", now);

        try {
            List<Post> duePosts = postRepository.findByStatusAndScheduledAtBefore(
                    PostStatus.SCHEDULED, now);

            if (duePosts.isEmpty()) {
                logger.debug("[Scheduler] No posts due for transmission at this time.");
                return;
            }

            logger.info("[Scheduler] Found {} posts due for transmission. Dispatching...", duePosts.size());

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
     * DYNAMIC SCHEDULER TICK (Runs every minute)
     * Checks all BusinessProfiles to see if it's time to generate drafts or auto-schedule.
     */
    @Scheduled(fixedRate = 60000)
    public void masterSchedulingTick() {
        LocalTime now = LocalTime.now(IST).withSecond(0).withNano(0);
        String timeStr = now.toString();
        logger.info("[Scheduler] Tick at {} IST", timeStr);

        try {
            // 1. Check for Morning Draft Generation
            List<BusinessProfile> morningDraftProfiles = businessProfileRepository.findByMorningDraftTime(timeStr);
            if (!morningDraftProfiles.isEmpty()) {
                autoPostService.generateDraftsForMatchingProfiles("MORNING", morningDraftProfiles);
            }

            // 2. Check for Evening Draft Generation
            List<BusinessProfile> eveningDraftProfiles = businessProfileRepository.findByEveningDraftTime(timeStr);
            if (!eveningDraftProfiles.isEmpty()) {
                autoPostService.generateDraftsForMatchingProfiles("EVENING", eveningDraftProfiles);
            }

            // 3. Check for Auto-Scheduling (Match publish time)
            checkAndAutoSchedule(timeStr);

        } catch (Exception e) {
            logger.error("❌ [Scheduler] Master tick failed: {}", e.getMessage(), e);
        }
    }

    private void checkAndAutoSchedule(String timeStr) {
        List<BusinessProfile> morningPub = businessProfileRepository.findByMorningPublishTime(timeStr);
        if (!morningPub.isEmpty()) {
            autoPostService.autoScheduleForProfiles("MORNING", morningPub, LocalDateTime.now(IST));
        }

        List<BusinessProfile> eveningPub = businessProfileRepository.findByEveningPublishTime(timeStr);
        if (!eveningPub.isEmpty()) {
            autoPostService.autoScheduleForProfiles("EVENING", eveningPub, LocalDateTime.now(IST));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // EVERGREEN QUEUE: Scheduled fill
    // ─────────────────────────────────────────────────────────────────

    /**
     * Evergreen Daily Check — runs every day at 06:30 AM IST.
     *
     * On TUESDAYS: proactively fills both the morning (09:00) and evening (20:00) slots
     *              for all users, even if no drafts were generated.
     *
     * On ALL days: fills morning and evening slots if they are still empty after
     *              the normal draft+auto-schedule cycle (safety net).
     */
    @Scheduled(cron = "0 30 6 * * *", zone = "Asia/Kolkata")
    public void evergreenDailyFill() {
        LocalDateTime now = LocalDateTime.now(IST);
        DayOfWeek today = now.getDayOfWeek();
        logger.info("[Evergreen] Daily fill check at {} ({})", now, today);

        LocalDateTime morningSlot = LocalDate.now(IST).atTime(9, 0);
        LocalDateTime eveningSlot = LocalDate.now(IST).atTime(20, 0);

        if (today == DayOfWeek.TUESDAY) {
            logger.info("📅 [Evergreen] Tuesday detected — running proactive evergreen fill for all users.");
            evergreenService.fillAllUsersEmptySlot(morningSlot, "MORNING");
            evergreenService.fillAllUsersEmptySlot(eveningSlot, "EVENING");
        } else {
            // Non-Tuesday: just plug any still-empty slots
            logger.info("🔍 [Evergreen] Non-Tuesday safety-net fill for any empty slots.");
            evergreenService.fillAllUsersEmptySlot(morningSlot, "MORNING");
            evergreenService.fillAllUsersEmptySlot(eveningSlot, "EVENING");
        }
    }
}
