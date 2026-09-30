package com.aiplatform.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Column(columnDefinition = "TEXT")
    private String caption;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "video_url", columnDefinition = "TEXT")
    private String videoUrl;

    @Column(columnDefinition = "TEXT")
    private String hashtags;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostStatus status; // DRAFT | SCHEDULED | PUBLISHED | FAILED

    @Column(nullable = true)
    private String platform; // FACEBOOK | INSTAGRAM | BOTH

    @Column(name = "slot_type")
    private String slotType; // MORNING | EVENING (which scheduling slot this post belongs to)

    @Builder.Default
    @Column(name = "auto_scheduled")
    private Boolean autoScheduled = false; // true if AI auto-scheduled without user approval

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "external_post_id", columnDefinition = "TEXT")
    private String externalPostId;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Builder.Default
    @Column(name = "is_thread")
    private Boolean isThread = false;

    @Column(name = "thread_content", columnDefinition = "TEXT")
    private String threadContent; // Stores a JSON-serialized list of captions for threads

    @Builder.Default
    @Column(name = "is_carousel")
    private Boolean isCarousel = false;

    @Column(name = "carousel_content", columnDefinition = "TEXT")
    private String carouselContent; // Stores a JSON-serialized list of slides for carousels

    @Builder.Default
    @Column(name = "is_story")
    private Boolean isStory = false;

    @Builder.Default
    @Column(name = "is_poll")
    private Boolean isPoll = false;

    @Builder.Default
    @Column(name = "is_reel")
    private Boolean isReel = false;

    @Column(name = "video_script", columnDefinition = "TEXT")
    private String videoScript; // Stores AI-generated reel script and scene details

    @Column(name = "poll_content", columnDefinition = "TEXT")
    private String pollContent; // JSON-serialized poll structure

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // ─── Evergreen Queue Fields ───────────────────────────────────

    /** True if the user has marked this post as evergreen for auto-recycling. */
    @Builder.Default
    @Column(name = "is_evergreen")
    private Boolean isEvergreen = false;

    /**
     * Performance score used to rank evergreen candidates.
     * Higher score = higher priority for auto-recycling.
     * Formula: (likes*1) + (comments*3) + (shares*5) - (ageInDays*0.1)
     */
    @Builder.Default
    @Column(name = "evergreen_score")
    private Double evergreenScore = 0.0;

    /** Tracks when this post was last auto-recycled to prevent spam reposts. */
    @Column(name = "last_recycled_at")
    private LocalDateTime lastRecycledAt;

    // ─── Engagement Fields (synced from social platform APIs) ─────────────────

    @Builder.Default
    @Column(name = "likes")
    private Long likes = 0L;

    @Builder.Default
    @Column(name = "comments_count")
    private Long commentsCount = 0L;

    @Builder.Default
    @Column(name = "shares")
    private Long shares = 0L;

    @Builder.Default
    @Column(name = "reach")
    private Long reach = 0L;

    @Column(name = "engagement_synced_at")
    private LocalDateTime engagementSyncedAt;

    @JsonIgnore
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Comment> comments;
}
