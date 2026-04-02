package com.aiplatform.service;

import com.aiplatform.model.Post;
import com.aiplatform.model.PostStatus;
import com.aiplatform.model.User;
import com.aiplatform.repository.PostRepository;
import com.aiplatform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * EvergreenService — Manages the Evergreen Queue recycling system.
 *
 * Flow:
 *  1. User marks a PUBLISHED post as evergreen → score is computed and stored.
 *  2. On Tuesdays (or any empty slot), PostSchedulerTask calls fillEmptySlotWithEvergreen().
 *  3. The best-scoring, not-recently-recycled post is cloned as a new SCHEDULED post.
 */
@Service
public class EvergreenService {

    private static final Logger logger = LoggerFactory.getLogger(EvergreenService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /**
     * Anti-spam guard: a post won't be recycled again within this many days.
     * Prevents the same post from being republished every single week.
     */
    private static final int RECYCLE_COOLDOWN_DAYS = 14;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    // ─────────────────────────────────────────────────────────────────
    // MARK / UNMARK EVERGREEN
    // ─────────────────────────────────────────────────────────────────

    /**
     * Marks a post as evergreen and computes its initial performance score.
     * Only PUBLISHED posts can be marked evergreen — they have real engagement data.
     *
     * @param postId  Post to mark
     * @param userId  Authenticated user (ownership check)
     * @return The updated post
     */
    @Transactional
    public Post markAsEvergreen(Long postId, Long userId) {
        Post post = findAndVerifyOwnership(postId, userId);

        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new IllegalStateException(
                "Only PUBLISHED posts can be added to the Evergreen Queue. " +
                "Post " + postId + " has status: " + post.getStatus());
        }

        post.setIsEvergreen(true);
        post.setEvergreenScore(computeScore(post));

        Post saved = postRepository.save(post);
        logger.info("🌿 [Evergreen] Post id={} marked as evergreen with score={} for user id={}",
                postId, saved.getEvergreenScore(), userId);
        return saved;
    }

    /**
     * Removes a post from the Evergreen Queue.
     */
    @Transactional
    public Post unmarkEvergreen(Long postId, Long userId) {
        Post post = findAndVerifyOwnership(postId, userId);
        post.setIsEvergreen(false);
        Post saved = postRepository.save(post);
        logger.info("🍂 [Evergreen] Post id={} removed from evergreen queue for user id={}", postId, userId);
        return saved;
    }

    // ─────────────────────────────────────────────────────────────────
    // LIST EVERGREEN POSTS
    // ─────────────────────────────────────────────────────────────────

    /**
     * Returns all evergreen posts for a user, sorted by score (highest first).
     */
    public List<Post> getEvergreenPostsForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        return postRepository.findByUserAndIsEvergreenTrueOrderByEvergreenScoreDesc(user);
    }

    // ─────────────────────────────────────────────────────────────────
    // AUTO-FILL EMPTY SLOT
    // ─────────────────────────────────────────────────────────────────

    /**
     * Core recycling logic. Called by the scheduler when a publishing slot is empty.
     *
     * Checks if the user already has a SCHEDULED post in the given slot window.
     * If not, picks the best evergreen candidate and schedules a clone of it.
     *
     * @param user        The user to check and fill for
     * @param slotTime    The exact IST datetime for which the slot is being filled
     * @param slotLabel   Human-readable label for logging (e.g. "MORNING", "EVENING")
     * @return The newly scheduled recycled post, or empty if slot was already filled / no candidates
     */
    @Transactional
    public Optional<Post> fillEmptySlotWithEvergreen(User user, LocalDateTime slotTime, String slotLabel) {
        // 1. Check if there is already something scheduled in a ±30-minute window
        LocalDateTime windowStart = slotTime.minusMinutes(30);
        LocalDateTime windowEnd   = slotTime.plusMinutes(30);

        boolean slotAlreadyFilled = postRepository.existsByUserAndStatusAndScheduledAtBetween(
                user, PostStatus.SCHEDULED, windowStart, windowEnd);

        if (slotAlreadyFilled) {
            logger.debug("✅ [Evergreen] {} slot at {} for user {} is already filled. Skipping.",
                    slotLabel, slotTime, user.getEmail());
            return Optional.empty();
        }

        // 2. Find the best candidate (not recycled within cooldown window)
        LocalDateTime cooldownThreshold = LocalDateTime.now(IST).minusDays(RECYCLE_COOLDOWN_DAYS);
        List<Post> candidates = postRepository.findTopEvergreenCandidates(user, cooldownThreshold);

        if (candidates.isEmpty()) {
            logger.info("📭 [Evergreen] No eligible evergreen candidates for user {}.", user.getEmail());
            return Optional.empty();
        }

        Post original = candidates.get(0);

        // 3. Clone the original post as a new SCHEDULED post
        Post recycledPost = Post.builder()
                .user(user)
                .caption(original.getCaption())
                .hashtags(original.getHashtags())
                .imageUrl(original.getImageUrl())
                .platform(original.getPlatform())
                .status(PostStatus.SCHEDULED)
                .scheduledAt(slotTime)
                .slotType(slotLabel)
                .autoScheduled(true)
                .isEvergreen(false) // The clone itself is not evergreen — the original is
                .createdAt(LocalDateTime.now(IST))
                .build();

        Post saved = postRepository.save(recycledPost);

        // 4. Update original's lastRecycledAt to respect cooldown
        original.setLastRecycledAt(LocalDateTime.now(IST));
        // Bump score slightly for posts that get recycled — they're proven performers
        original.setEvergreenScore(original.getEvergreenScore() + 2.0);
        postRepository.save(original);

        logger.info("♻️  [Evergreen] Recycled post id={} (score={}) → new SCHEDULED post id={} at {} for user {}",
                original.getId(), original.getEvergreenScore(), saved.getId(), slotTime, user.getEmail());

        return Optional.of(saved);
    }

    /**
     * Convenience method: runs the evergreen fill for ALL users.
     * Called by PostSchedulerTask on Tuesdays or when slots are found empty.
     */
    @Transactional
    public void fillAllUsersEmptySlot(LocalDateTime slotTime, String slotLabel) {
        List<User> allUsers = userRepository.findAll();
        int filled = 0;

        for (User user : allUsers) {
            try {
                Optional<Post> result = fillEmptySlotWithEvergreen(user, slotTime, slotLabel);
                if (result.isPresent()) filled++;
            } catch (Exception e) {
                logger.error("❌ [Evergreen] Fill failed for user {}: {}", user.getEmail(), e.getMessage());
            }
        }

        logger.info("♻️  [Evergreen] Bulk fill complete — {} posts recycled across {} users for {} slot at {}",
                filled, allUsers.size(), slotLabel, slotTime);
    }

    // ─────────────────────────────────────────────────────────────────
    // SCORING
    // ─────────────────────────────────────────────────────────────────

    /**
     * Computes the evergreen performance score from available post metadata.
     *
     * Since we don't store raw engagement counts directly on Post (they come from
     * the Insights APIs), we derive a proxy score based on what we do know:
     *  - Posts published longer ago get a small decay penalty (promotes content freshness)
     *  - Posts that were auto-scheduled start with a base score lower than manually approved ones
     *    (manual approval = human curation signal)
     *
     * When real engagement data is available (via scheduled insight sync), this method
     * can be enhanced to incorporate likes, comments, reach.
     *
     * Current formula:
     *   score = base − (ageInDays × 0.1) + (wasManuallyApproved ? 5 : 0)
     *   base  = 50.0
     */
    public double computeScore(Post post) {
        double base = 50.0;

        // Age decay: older posts score slightly lower
        long ageInDays = 0;
        if (post.getPublishedAt() != null) {
            ageInDays = ChronoUnit.DAYS.between(post.getPublishedAt(), LocalDateTime.now(IST));
        } else if (post.getCreatedAt() != null) {
            ageInDays = ChronoUnit.DAYS.between(post.getCreatedAt(), LocalDateTime.now(IST));
        }
        double agePenalty = ageInDays * 0.1;

        // Human curation bonus: user-approved posts are ranked higher
        double curationBonus = Boolean.FALSE.equals(post.getAutoScheduled()) ? 5.0 : 0.0;

        return Math.max(0, base - agePenalty + curationBonus);
    }

    // ─────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────

    private Post findAndVerifyOwnership(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found: " + postId));
        if (!post.getUser().getId().equals(userId)) {
            throw new SecurityException("Post " + postId + " does not belong to user " + userId);
        }
        return post;
    }
}
