package com.aiplatform.repository;

import com.aiplatform.model.Post;
import com.aiplatform.model.PostStatus;
import com.aiplatform.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByUser(User user);
    List<Post> findByUserOrderByCreatedAtDesc(User user);
    List<Post> findByStatus(PostStatus status);
    List<Post> findByStatusAndScheduledAtBefore(PostStatus status, LocalDateTime dateTime);
    List<Post> findByUserAndStatus(User user, PostStatus status);
    List<Post> findByUserAndStatusAndSlotType(User user, PostStatus status, String slotType);
    List<Post> findByStatusAndSlotTypeAndCreatedAtAfter(PostStatus status, String slotType, LocalDateTime after);
    List<Post> findByUserAndStatusAndCreatedAtAfter(User user, PostStatus status, LocalDateTime after);
    List<Post> findByUserAndStatusAndSlotTypeAndCreatedAtAfter(User user, PostStatus status, String slotType, LocalDateTime after);

    // ─── Evergreen Queue Queries ───────────────────────────────────────────

    /** All evergreen posts for a user, ordered by score desc */
    List<Post> findByUserAndIsEvergreenTrueOrderByEvergreenScoreDesc(User user);

    /** Best evergreen candidate not recycled since a given time */
    @Query("SELECT p FROM Post p WHERE p.user = :user AND p.isEvergreen = true " +
           "AND p.status = 'PUBLISHED' " +
           "AND (p.lastRecycledAt IS NULL OR p.lastRecycledAt < :notSince) " +
           "ORDER BY p.evergreenScore DESC")
    List<Post> findTopEvergreenCandidates(
            @Param("user") User user,
            @Param("notSince") LocalDateTime notSince);

    /** Check if a scheduled post already exists in the given time window */
    boolean existsByUserAndStatusAndScheduledAtBetween(
            User user, PostStatus status, LocalDateTime start, LocalDateTime end);
}
