package com.aiplatform.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_comment_id")
    private String externalCommentId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "author_name")
    private String authorName;

    @Column(name = "author_profile_picture_url", length = 1000)
    private String authorProfilePictureUrl;

    @Column(nullable = false)
    private String platform; // FACEBOOK | INSTAGRAM

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "is_replied")
    private Boolean isReplied = false;

    @Column(name = "ai_draft_reply", columnDefinition = "TEXT")
    private String aiDraftReply;

    @Column(name = "sentiment")
    private String sentiment; // POSITIVE | NEGATIVE | QUESTION | SPAM

    @Column(name = "priority")
    private String priority; // HIGH | MEDIUM | LOW

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;
}
