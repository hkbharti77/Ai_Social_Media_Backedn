package com.aiplatform.repository;

import com.aiplatform.model.Post;
import com.aiplatform.model.PostStatus;
import com.aiplatform.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByUser(User user);
    List<Post> findByStatus(PostStatus status);
    List<Post> findByStatusAndScheduledAtBefore(PostStatus status, LocalDateTime dateTime);
    List<Post> findByUserAndStatus(User user, PostStatus status);
    List<Post> findByUserAndStatusAndSlotType(User user, PostStatus status, String slotType);
    List<Post> findByStatusAndSlotTypeAndCreatedAtAfter(PostStatus status, String slotType, LocalDateTime after);
}
