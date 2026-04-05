package com.aiplatform.service;

import com.aiplatform.model.BusinessProfile;
import com.aiplatform.model.Comment;
import com.aiplatform.model.Post;
import com.aiplatform.model.User;
import com.aiplatform.repository.CommentRepository;
import com.aiplatform.repository.PostRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class CommunityManagerService {
    private static final Logger logger = LoggerFactory.getLogger(CommunityManagerService.class);

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final AiContentService aiContentService;
    private final BusinessProfileService businessProfileService;

    /**
     * Simulates fetching new comments from social platforms.
     * In a real app, this would use Webhooks or poll the Graph API.
     */
    @Transactional
    public List<Comment> syncComments(User user) {
        List<Post> recentPosts = postRepository.findByUserOrderByCreatedAtDesc(user);
        if (recentPosts.isEmpty()) return new ArrayList<>();

        List<Comment> newComments = new ArrayList<>();
        Random random = new Random();
        
        // Mock data generators
        String[] mockNames = {"Arjun Mehta", "Sanya Kapoor", "Vikram Singh", "Priya Sharma", "Rahul Verma"};
        String[] mockComments = {
            "Amazing quality! Loving the new collection.",
            "Why is the delivery so late? Still waiting for my order #12345.",
            "Can I get this in blue color?",
            "Fake brand, don't buy from them.",
            "This post changed my perspective, thanks for sharing!",
            "Price please?",
            "Do you ship to Bangalore?"
        };

        // Pick a random post to add a comment to
        Post targetPost = recentPosts.get(random.nextInt(recentPosts.size()));
        
        Comment comment = Comment.builder()
                .externalCommentId("MOCK_" + System.currentTimeMillis())
                .text(mockComments[random.nextInt(mockComments.length)])
                .authorName(mockNames[random.nextInt(mockNames.length)])
                .platform(targetPost.getPlatform() != null ? targetPost.getPlatform() : "INSTAGRAM")
                .createdAt(LocalDateTime.now())
                .user(user)
                .post(targetPost)
                .isReplied(false)
                .build();

        // Perform AI Triage
        BusinessProfile bp = businessProfileService.getProfile(user.getId());
        JsonNode analysis = aiContentService.analyzeCommunitySentiment(bp, comment.getText());
        
        comment.setSentiment(analysis.path("sentiment").asText("NEUTRAL"));
        comment.setPriority(analysis.path("priority").asText("MEDIUM"));
        
        Comment saved = commentRepository.save(comment);
        newComments.add(saved);
        
        logger.info("✅ Synced {} new comments for user {}", newComments.size(), user.getEmail());
        return newComments;
    }

    @Transactional
    public String draftReply(Long commentId, User user) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));
        
        BusinessProfile bp = businessProfileService.getProfile(user.getId());
        String postContext = comment.getPost() != null ? comment.getPost().getCaption() : "General engagement";
        
        String draft = aiContentService.generateCommunityProReply(bp, comment.getText(), postContext, null);
        comment.setAiDraftReply(draft);
        commentRepository.save(comment);
        
        return draft;
    }

    @Transactional
    public void sendReply(Long commentId, String replyText, User user) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));
        
        // In a real app, this would call the FB/IG API to post the comment
        logger.info("🚀 [SIMULATION] Sending reply to {} on {}: {}", comment.getAuthorName(), comment.getPlatform(), replyText);
        
        comment.setIsReplied(true);
        commentRepository.save(comment);
    }

    public List<Comment> getInbox(User user, String sentiment, String priority) {
        // Simple filtering logic
        return commentRepository.findByUserOrderByCreatedAtDesc(user);
    }
}
