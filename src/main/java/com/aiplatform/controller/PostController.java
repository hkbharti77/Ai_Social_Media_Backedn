package com.aiplatform.controller;

import com.aiplatform.dto.DashboardStats;
import com.aiplatform.model.Post;
import com.aiplatform.model.PostStatus;
import com.aiplatform.model.User;
import com.aiplatform.repository.PostRepository;
import com.aiplatform.repository.UserRepository;
import com.aiplatform.security.UserDetailsImpl;
import com.aiplatform.service.AutoPostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private static final Logger logger = LoggerFactory.getLogger(PostController.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AutoPostService autoPostService;

    @GetMapping
    public ResponseEntity<List<Post>> getPosts() {
        try {
            UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findById(userDetails.getId())
                    .orElseThrow(() -> new RuntimeException("User not found: " + userDetails.getId()));
            
            List<Post> posts = postRepository.findByUser(user);
            logger.info("📡 [PostController] Fetched {} posts for user {}", posts.size(), user.getEmail());
            return ResponseEntity.ok(posts);
        } catch (Exception e) {
            logger.error("❌ [PostController] Error in getPosts: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * GET /api/v1/posts/drafts
     * Returns all DRAFT posts for the current user (pending review queue).
     */
    @GetMapping("/drafts")
    public ResponseEntity<List<Post>> getDraftPosts() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepository.findById(userDetails.getId()).get();
        return ResponseEntity.ok(postRepository.findByUserAndStatus(user, PostStatus.DRAFT));
    }

    @GetMapping("/stats")
    public ResponseEntity<DashboardStats> getStats() {
        try {
            UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findById(userDetails.getId())
                    .orElseThrow(() -> new RuntimeException("User not found: " + userDetails.getId()));
            
            List<Post> posts = postRepository.findByUser(user);
            
            DashboardStats stats = DashboardStats.builder()
                    .draftCount(posts.stream().filter(p -> p.getStatus() == PostStatus.DRAFT).count())
                    .scheduledCount(posts.stream().filter(p -> p.getStatus() == PostStatus.SCHEDULED).count())
                    .publishedCount(posts.stream().filter(p -> p.getStatus() == PostStatus.PUBLISHED).count())
                    .failedCount(posts.stream().filter(p -> p.getStatus() == PostStatus.FAILED).count())
                    .build();
            
            logger.info("📊 [PostController] Fetched stats for user {}: drafts={}", user.getEmail(), stats.getDraftCount());
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("❌ [PostController] Error in getStats: {}", e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping
    public ResponseEntity<Post> createPost(@RequestBody Post post) {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepository.findById(userDetails.getId()).get();
        post.setUser(user);
        if (post.getStatus() == null) {
            post.setStatus(PostStatus.DRAFT);
        }
        return ResponseEntity.ok(postRepository.save(post));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Post> updatePost(@PathVariable Long id, @RequestBody Post postUpdates) {
        Post existing = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        existing.setCaption(postUpdates.getCaption());
        existing.setHashtags(postUpdates.getHashtags());
        existing.setImageUrl(postUpdates.getImageUrl());
        existing.setPlatform(postUpdates.getPlatform());
        existing.setStatus(postUpdates.getStatus());
        existing.setScheduledAt(postUpdates.getScheduledAt());
        if (postUpdates.getSlotType() != null) {
            existing.setSlotType(postUpdates.getSlotType());
        }

        return ResponseEntity.ok(postRepository.save(existing));
    }

    /**
     * PUT /api/v1/posts/{id}/approve
     * User approves a draft → changes to SCHEDULED at the appropriate IST slot time.
     * Morning slot (MORNING) schedules at 9:00 AM, Evening (EVENING) at 8:00 PM.
     */
    @PutMapping("/{id}/approve")
    public ResponseEntity<Post> approveDraftPost(@PathVariable Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        if (post.getStatus() != PostStatus.DRAFT) {
            return ResponseEntity.badRequest().build();
        }

        LocalDateTime publishAt;
        if ("MORNING".equals(post.getSlotType())) {
            publishAt = LocalDate.now(IST).atTime(9, 0);
        } else if ("EVENING".equals(post.getSlotType())) {
            publishAt = LocalDate.now(IST).atTime(20, 0);
        } else {
            // Manual or no-slot post: schedule 5 minutes from now
            publishAt = LocalDateTime.now(IST).plusMinutes(5);
        }

        post.setStatus(PostStatus.SCHEDULED);
        post.setScheduledAt(publishAt);
        post.setAutoScheduled(false); // User explicitly approved

        return ResponseEntity.ok(postRepository.save(post));
    }

    /**
     * POST /api/v1/posts/generate-draft?slot=MORNING|EVENING
     * Manual trigger for draft generation (useful for testing).
     */
    @PostMapping("/generate-draft")
    public ResponseEntity<Post> generateDraftForCurrentUser(@RequestParam String slot) {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepository.findById(userDetails.getId()).get();
        Post created = autoPostService.generateDraftForUser(user, slot.toUpperCase());
        if (created == null) {
            return ResponseEntity.ok().build(); // Already has a draft today
        }
        return ResponseEntity.ok(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePost(@PathVariable Long id) {
        postRepository.deleteById(id);
        return ResponseEntity.ok("Post deleted successfully");
    }

    @PostMapping("/{id}/schedule")
    public ResponseEntity<Post> schedulePost(@PathVariable Long id, @RequestParam String scheduledAt) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        
        post.setScheduledAt(LocalDateTime.parse(scheduledAt));
        post.setStatus(PostStatus.SCHEDULED);
        
        return ResponseEntity.ok(postRepository.save(post));
    }
}
