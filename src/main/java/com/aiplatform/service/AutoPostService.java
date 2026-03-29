package com.aiplatform.service;

import com.aiplatform.dto.ContentGenerationDtos.GeneratedPost;
import com.aiplatform.model.BusinessProfile;
import com.aiplatform.model.Post;
import com.aiplatform.model.PostStatus;
import com.aiplatform.model.User;
import com.aiplatform.repository.BusinessProfileRepository;
import com.aiplatform.repository.PostRepository;
import com.aiplatform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * AutoPostService – Core orchestrator for the IST-based auto draft + scheduling workflow.
 *
 * Flow:
 *  1. At 6:00 AM IST → generateDraftForSlot(MORNING) for all users
 *  2. At 3:00 PM IST → generateDraftForSlot(EVENING) for all users
 *  3. At 8:00 AM IST → autoScheduleIfNotApproved(MORNING, 9 AM today)
 *  4. At 6:00 PM IST → autoScheduleIfNotApproved(EVENING, 8 PM today)
 */
@Service
public class AutoPostService {

    private static final Logger logger = LoggerFactory.getLogger(AutoPostService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BusinessProfileRepository businessProfileRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private AiContentService aiContentService;

    // ───────────────────────────────────────────────────────────────
    // DRAFT GENERATION
    // ───────────────────────────────────────────────────────────────

    /**
     * Generates a draft post for the given slot (MORNING or EVENING) for ALL users
     * who have a BusinessProfile configured. Skips users who already have a draft
     * for this slot today.
     */
    @Transactional
    public void generateDraftsForAllUsers(String slotType) {
        logger.info("📝 [AutoPost] Starting draft generation for slot: {}", slotType);
        List<User> allUsers = userRepository.findAll();

        for (User user : allUsers) {
            try {
                generateDraftForUser(user, slotType);
            } catch (Exception e) {
                logger.error("❌ [AutoPost] Failed to generate {} draft for user {}: {}",
                        slotType, user.getEmail(), e.getMessage(), e);
            }
        }
        logger.info("✅ [AutoPost] Draft generation complete for slot: {}", slotType);
    }

    /**
     * Generates a draft for a single user for the given slot if one doesn't already exist today.
     */
    @Transactional
    public Post generateDraftForUser(User user, String slotType) {
        // Check if a draft already exists for this slot today
        LocalDateTime startOfDay = LocalDate.now(IST).atStartOfDay();
        List<Post> existingDrafts = postRepository.findByUserAndStatusAndSlotType(
                user, PostStatus.DRAFT, slotType);

        boolean hasToday = existingDrafts.stream()
                .anyMatch(p -> p.getCreatedAt() != null && p.getCreatedAt().isAfter(startOfDay));

        if (hasToday) {
            logger.info("⏭️ [AutoPost] User {} already has a {} draft for today – skipping.",
                    user.getEmail(), slotType);
            return null;
        }

        // Load business profile
        Optional<BusinessProfile> bpOpt = businessProfileRepository.findByUser(user);
        if (bpOpt.isEmpty()) {
            logger.warn("⚠️ [AutoPost] No BusinessProfile found for user {} – skipping.", user.getEmail());
            return null;
        }

        BusinessProfile bp = bpOpt.get();
        String command = buildSlotCommand(slotType, bp);

        logger.info("🤖 [AutoPost] Generating {} draft for user {} (business: {})",
                slotType, user.getEmail(), bp.getBusinessName());

        GeneratedPost generated = aiContentService.generatePost(bp, command, user.getId());

        Post post = Post.builder()
                .user(user)
                .caption(generated.getCaption())
                .hashtags(generated.getHashtags() != null
                        ? String.join(" ", generated.getHashtags())
                        : "")
                .imageUrl(generated.getImageUrl())
                .status(PostStatus.DRAFT)
                .slotType(slotType)
                .platform("BOTH")
                .autoScheduled(false)
                .createdAt(LocalDateTime.now(IST))
                .build();

        Post saved = postRepository.save(post);
        logger.info("💾 [AutoPost] Saved {} draft post id={} for user {}", slotType, saved.getId(), user.getEmail());
        return saved;
    }

    // ───────────────────────────────────────────────────────────────
    // AUTO-SCHEDULE IF NOT APPROVED
    // ───────────────────────────────────────────────────────────────

    /**
     * Checks all DRAFT posts for today's given slot. If any are still DRAFT
     * (not approved by user), auto-schedules them to publish at the given time.
     *
     * @param slotType   MORNING or EVENING
     * @param publishAt  The IST datetime at which to publish (9 AM or 8 PM)
     */
    @Transactional
    public void autoScheduleIfNotApproved(String slotType, LocalDateTime publishAt) {
        logger.info("⏰ [AutoPost] Checking unapproved {} drafts – will schedule for {}", slotType, publishAt);

        LocalDateTime startOfDay = LocalDate.now(IST).atStartOfDay();

        List<Post> unapprovedDrafts = postRepository.findByStatusAndSlotTypeAndCreatedAtAfter(
                PostStatus.DRAFT, slotType, startOfDay);

        if (unapprovedDrafts.isEmpty()) {
            logger.info("✅ [AutoPost] No unapproved {} drafts found – all approved by users.", slotType);
            return;
        }

        logger.info("🚀 [AutoPost] Auto-scheduling {} unapproved {} draft(s) for {}",
                unapprovedDrafts.size(), slotType, publishAt);

        for (Post post : unapprovedDrafts) {
            post.setStatus(PostStatus.SCHEDULED);
            post.setScheduledAt(publishAt);
            post.setAutoScheduled(true);
            postRepository.save(post);
            logger.info("📅 [AutoPost] Post id={} auto-scheduled for {} (user: {})",
                    post.getId(), publishAt, post.getUser().getEmail());
        }
    }

    // ───────────────────────────────────────────────────────────────
    // HELPERS
    // ───────────────────────────────────────────────────────────────

    private String buildSlotCommand(String slotType, BusinessProfile bp) {
        String businessName = bp.getBusinessName() != null ? bp.getBusinessName() : "our brand";
        if ("MORNING".equals(slotType)) {
            return String.format(
                    "Create a fresh, energetic morning post for %s. " +
                    "It should inspire and motivate the audience to start their day. " +
                    "Keep it uplifting and on-brand.",
                    businessName);
        } else {
            return String.format(
                    "Create an engaging evening post for %s. " +
                    "It should wind down the day, celebrate achievements, or encourage reflection. " +
                    "Keep it warm, conversational and on-brand.",
                    businessName);
        }
    }
}
