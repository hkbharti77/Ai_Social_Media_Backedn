package com.aiplatform.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

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

    @Column(columnDefinition = "TEXT")
    private String caption;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

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
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
