package com.aiplatform.controller;

import com.aiplatform.dto.DashboardStats;
import com.aiplatform.model.Post;
import com.aiplatform.model.PostStatus;
import com.aiplatform.model.User;
import com.aiplatform.repository.PostRepository;
import com.aiplatform.service.AutoPostService;
import com.aiplatform.service.EvergreenService;
import com.aiplatform.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private static final Logger logger = LoggerFactory.getLogger(PostController.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final PostRepository postRepository;
    private final AutoPostService autoPostService;
    private final EvergreenService evergreenService;

    @GetMapping
    public ResponseEntity<List<Post>> getPosts() {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        List<Post> posts = postRepository.findByUser(user);
        logger.info("📡 [PostController] Fetched {} posts for user {}", posts.size(), user.getEmail());
        return ResponseEntity.ok(posts);
    }

    @GetMapping("/drafts")
    public ResponseEntity<List<Post>> getDraftPosts() {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        return ResponseEntity.ok(postRepository.findByUserAndStatus(user, PostStatus.DRAFT));
    }

    @GetMapping("/stats")
    public ResponseEntity<DashboardStats> getStats() {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        List<Post> posts = postRepository.findByUser(user);
        
        DashboardStats stats = DashboardStats.builder()
                .draftCount(posts.stream().filter(p -> p.getStatus() == PostStatus.DRAFT).count())
                .scheduledCount(posts.stream().filter(p -> p.getStatus() == PostStatus.SCHEDULED).count())
                .publishedCount(posts.stream().filter(p -> p.getStatus() == PostStatus.PUBLISHED).count())
                .failedCount(posts.stream().filter(p -> p.getStatus() == PostStatus.FAILED).count())
                .build();
        
        logger.info("📊 [PostController] Fetched stats for user {}: drafts={}", user.getEmail(), stats.getDraftCount());
        return ResponseEntity.ok(stats);
    }

    @PostMapping
    public ResponseEntity<Post> createPost(@Valid @RequestBody Post post) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
        
        // SECURITY: Clear sensitive/injected fields
        post.setId(null);
        post.setUser(user);
        
        if (post.getStatus() == null) {
            post.setStatus(PostStatus.DRAFT);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(postRepository.save(post));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Post> updatePost(@PathVariable Long id, @Valid @RequestBody Post postUpdates) {
        Long userId = SecurityUtils.getCurrentUserId();
        Post existing = postRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));

        if (!existing.getUser().getId().equals(userId)) {
             throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied: You do not own this post.");
        }

        existing.setCaption(postUpdates.getCaption());
        existing.setHashtags(postUpdates.getHashtags());
        existing.setImageUrl(postUpdates.getImageUrl());
        existing.setVideoUrl(postUpdates.getVideoUrl());
        existing.setPlatform(postUpdates.getPlatform());
        existing.setStatus(postUpdates.getStatus());
        existing.setIsReel(postUpdates.getIsReel());
        existing.setVideoScript(postUpdates.getVideoScript());
        existing.setScheduledAt(postUpdates.getScheduledAt());
        if (postUpdates.getSlotType() != null) {
            existing.setSlotType(postUpdates.getSlotType());
        }

        // Support Carousel updates
        if (postUpdates.getIsCarousel() != null) {
            existing.setIsCarousel(postUpdates.getIsCarousel());
        }
        if (postUpdates.getCarouselContent() != null) {
            existing.setCarouselContent(postUpdates.getCarouselContent());
        }

        // Support Story & Poll updates
        if (postUpdates.getIsStory() != null) {
            existing.setIsStory(postUpdates.getIsStory());
        }
        if (postUpdates.getIsPoll() != null) {
            existing.setIsPoll(postUpdates.getIsPoll());
        }
        if (postUpdates.getPollContent() != null) {
            existing.setPollContent(postUpdates.getPollContent());
        }

        return ResponseEntity.ok(postRepository.save(existing));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<Post> approveDraftPost(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        if (!post.getUser().getId().equals(userId)) {
            return ResponseEntity.status(404).build(); 
        }

        if (post.getStatus() != PostStatus.DRAFT) {
            return ResponseEntity.badRequest().build();
        }

        LocalDateTime publishAt;
        if ("MORNING".equals(post.getSlotType())) {
            publishAt = LocalDate.now(IST).atTime(9, 0);
        } else if ("EVENING".equals(post.getSlotType())) {
            publishAt = LocalDate.now(IST).atTime(20, 0);
        } else {
            publishAt = LocalDateTime.now(IST).plusMinutes(5);
        }

        post.setStatus(PostStatus.SCHEDULED);
        post.setScheduledAt(publishAt);
        post.setAutoScheduled(false); 

        return ResponseEntity.ok(postRepository.save(post));
    }

    @PostMapping("/generate-draft")
    public ResponseEntity<Post> generateDraftForCurrentUser(@RequestParam String slot) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
        Post created = autoPostService.generateDraftForUser(user, slot.toUpperCase(), null);
        if (created == null) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.ok(created);
    }

    private LocalDateTime parseDate(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            // Try ISO with offset (e.g., 2026-04-05T03:30:00.000Z)
            return ZonedDateTime.parse(str).toLocalDateTime();
        } catch (Exception e) {
            try {
                // Try ISO with offset (alternate)
                return OffsetDateTime.parse(str).toLocalDateTime();
            } catch (Exception e2) {
                try {
                    // Try basic LocalDateTime (e.g., 2026-04-06T07:08:00)
                    return LocalDateTime.parse(str);
                } catch (Exception e3) {
                    // Handle yyyy-MM-ddTHH:mm by appending :00
                    if (str.length() == 16) {
                        return LocalDateTime.parse(str + ":00");
                    }
                    throw e3;
                }
            }
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePost(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        if (!post.getUser().getId().equals(userId)) {
            return ResponseEntity.status(404).build(); 
        }

        postRepository.delete(post);
        return ResponseEntity.ok("Post deleted successfully");
    }

    @PostMapping("/{id}/schedule")
    public ResponseEntity<Post> schedulePost(@PathVariable Long id, @RequestParam String scheduledAt) {
        Long userId = SecurityUtils.getCurrentUserId();
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        if (!post.getUser().getId().equals(userId)) {
            return ResponseEntity.status(404).build();
        }
        
        post.setScheduledAt(parseDate(scheduledAt));
        post.setStatus(PostStatus.SCHEDULED);
        
        return ResponseEntity.ok(postRepository.save(post));
    }

    @PostMapping("/{id}/recycle")
    public ResponseEntity<Post> recyclePost(@PathVariable Long id, @RequestParam String scheduledAt) {
        Long userId = SecurityUtils.getCurrentUserId();
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        if (!post.getUser().getId().equals(userId)) {
            return ResponseEntity.status(404).build();
        }

        // Clone the post as a new scheduled one
        Post recycled = Post.builder()
                .user(post.getUser())
                .caption(post.getCaption())
                .hashtags(post.getHashtags())
                .imageUrl(post.getImageUrl())
                .videoUrl(post.getVideoUrl())
                .platform(post.getPlatform())
                .status(PostStatus.SCHEDULED)
                .scheduledAt(parseDate(scheduledAt))
                .isEvergreen(false)
                .autoScheduled(false)
                .createdAt(LocalDateTime.now())
                .build();
        
        return ResponseEntity.ok(postRepository.save(recycled));
    }

    // ─────────────────────────────────────────────────────────────────
    // EVERGREEN QUEUE ENDPOINTS
    // ─────────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/posts/evergreen
     * Returns all posts marked as evergreen for the current user, ordered by score.
     */
    @GetMapping("/evergreen")
    public ResponseEntity<List<Post>> getEvergreenPosts() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<Post> evergreenPosts = evergreenService.getEvergreenPostsForUser(userId);
        logger.info("🌿 [PostController] Fetched {} evergreen posts for user id={}", evergreenPosts.size(), userId);
        return ResponseEntity.ok(evergreenPosts);
    }

    /**
     * PUT /api/v1/posts/{id}/evergreen
     * Marks a PUBLISHED post as evergreen and computes its initial score.
     */
    @PutMapping("/{id}/evergreen")
    public ResponseEntity<Post> markEvergreen(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        try {
            Post updated = evergreenService.markAsEvergreen(id, userId);
            return ResponseEntity.ok(updated);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    /**
     * DELETE /api/v1/posts/{id}/evergreen
     * Removes a post from the Evergreen Queue.
     */
    @DeleteMapping("/{id}/evergreen")
    public ResponseEntity<Post> unmarkEvergreen(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        try {
            Post updated = evergreenService.unmarkEvergreen(id, userId);
            return ResponseEntity.ok(updated);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    /**
     * POST /api/v1/posts/evergreen/fill
     * Manually triggers an evergreen fill for the current user's empty slots.
     * Useful for testing and for users who want to immediately recycle content.
     */
    @PostMapping("/evergreen/fill")
    public ResponseEntity<Map<String, Object>> triggerEvergreenFill(
            @RequestParam(defaultValue = "MORNING") String slot) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

        ZoneId ist = ZoneId.of("Asia/Kolkata");
        LocalDateTime slotTime;
        if ("EVENING".equalsIgnoreCase(slot)) {
            slotTime = LocalDate.now(ist).atTime(20, 0);
        } else {
            slotTime = LocalDate.now(ist).atTime(9, 0);
        }

        var result = evergreenService.fillEmptySlotWithEvergreen(user, slotTime, slot.toUpperCase());

        if (result.isPresent()) {
            return ResponseEntity.ok(Map.of(
                "message", "Evergreen post recycled successfully",
                "scheduledPostId", result.get().getId(),
                "scheduledAt", result.get().getScheduledAt().toString()
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                "message", "Slot already filled or no eligible evergreen posts found."
            ));
        }
    }
}
