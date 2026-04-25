package com.aiplatform.repository;

import com.aiplatform.model.Comment;
import com.aiplatform.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByUserOrderByCreatedAtDesc(User user);
    List<Comment> findByUserAndIsRepliedOrderByCreatedAtDesc(User user, boolean isReplied);
    boolean existsByExternalCommentId(String externalCommentId);
}
