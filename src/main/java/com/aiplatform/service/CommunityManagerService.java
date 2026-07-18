package com.aiplatform.service;

import com.aiplatform.model.BusinessProfile;
import com.aiplatform.model.Comment;
import com.aiplatform.model.Post;
import com.aiplatform.model.User;
import com.aiplatform.model.SocialAccount;
import com.aiplatform.repository.CommentRepository;
import com.aiplatform.repository.PostRepository;
import com.aiplatform.repository.SocialAccountRepository;
import com.aiplatform.security.EncryptionUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.MultiValueMap;
import org.springframework.util.LinkedMultiValueMap;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommunityManagerService {
    private static final Logger logger = LoggerFactory.getLogger(CommunityManagerService.class);

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final AiEngagementService aiEngagementService;
    private final BusinessProfileService businessProfileService;
    private final SocialAccountRepository socialAccountRepository;
    private final EncryptionUtils encryptionUtils;
    private final RestTemplate restTemplate;

    /**
     * Fetches real comments from social platforms (FB/IG) using Graph API.
     */
    @Transactional
    public List<Comment> syncComments(User user) {
        List<SocialAccount> accounts = socialAccountRepository.findByUser(user);
        List<Comment> syncedComments = new ArrayList<>();
        BusinessProfile bp = businessProfileService.getProfile(user.getId());

        for (SocialAccount account : accounts) {
            String platform = account.getPlatform().toUpperCase();
            String token;
            try {
                token = encryptionUtils.decrypt(account.getEncryptedAccessToken());
            } catch (Exception e) {
                logger.error("❌ Failed to decrypt token for {} account of user {}", platform, user.getEmail());
                continue;
            }

            // Sync comments for recent posts (Last 30 posts for better coverage)
            List<Post> posts = postRepository.findByUserOrderByCreatedAtDesc(user).stream().limit(30).toList();
            for (Post post : posts) {
                if (post.getExternalPostId() == null) continue;
                
                // Skip IG/FB Stories as they don't support public comments edge
                if (Boolean.TRUE.equals(post.getIsStory())) continue;

                // Sync only matching platform
                if (!post.getPlatform().equalsIgnoreCase(platform) && !post.getPlatform().equalsIgnoreCase("ALL")) continue;

                try {
                    String objectId = post.getExternalPostId();
                    
                    // Robust Facebook ID handling: prioritize page_post format
                    if (platform.equals("FACEBOOK") && !objectId.contains("_") && account.getPageId() != null) {
                        objectId = account.getPageId() + "_" + objectId;
                    }

                    fetchCommentsForObject(objectId, token, user, post, platform, bp, syncedComments);
                } catch (Exception e) {
                    // Try fallback for Facebook if the formatted ID failed
                    if (platform.equals("FACEBOOK") && post.getExternalPostId().contains("_")) {
                         // Maybe try with just the second part if the concatenated one failed (rare but happens)
                    }

                    if (!e.getMessage().contains("Unsupported get request") && !e.getMessage().contains("does not exist")) {
                        logger.warn("⚠️ Meta Sync Warning for {} {}: {}", platform, post.getExternalPostId(), e.getMessage());
                    }
                }
            }
        }

        return syncedComments;
    }

    private void fetchCommentsForObject(String objectId, String token, User user, Post post, String platform, BusinessProfile bp, List<Comment> syncedComments) {
        // Clean ID for IG and formatted ID for FB
        String cleanId = objectId.trim();
        String url = String.format("https://graph.facebook.com/v21.0/%s/comments?access_token=%s&fields=id,text,from,created_time", cleanId, token);
        
        try {
            JsonNode response = restTemplate.getForObject(url, JsonNode.class);
            if (response != null && response.has("data")) {
                for (JsonNode node : response.get("data")) {
                    JsonNode idNode = node.path("id");
                    if (idNode.isMissingNode() || idNode.isNull()) continue;

                    String externalId = idNode.asText();
                    if (commentRepository.existsByExternalCommentId(externalId)) continue;

                    String text = node.path("text").asText("Engagement");
                    String author = node.path("from").path("name").asText("Social User");
                    
                    Comment comment = Comment.builder()
                            .externalCommentId(externalId)
                            .text(text)
                            .authorName(author)
                            .platform(platform)
                            .user(user)
                            .post(post)
                            .isReplied(false)
                            .createdAt(LocalDateTime.now())
                            .build();

                    // AI Triage
                    JsonNode analysis = aiEngagementService.analyzeCommunitySentiment(bp, text);
                    comment.setSentiment(analysis.path("sentiment").asText("NEUTRAL"));
                    comment.setPriority(analysis.path("priority").asText("MEDIUM"));

                    commentRepository.save(comment);
                    syncedComments.add(comment);
                }
            }
        } catch (Exception e) {
            logger.warn("⚠️ Meta API Warning for {}: {}", objectId, e.getMessage());
        }
    }

    @Transactional
    public String draftReply(Long commentId, User user) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));
        
        BusinessProfile bp = businessProfileService.getProfile(user.getId());
        String postContext = comment.getPost() != null ? comment.getPost().getCaption() : "General engagement";
        
        String draft = aiEngagementService.generateCommunityProReply(bp, comment.getText(), postContext, null);
        comment.setAiDraftReply(draft);
        commentRepository.save(comment);
        
        return draft;
    }

    @Transactional
    public void sendReply(Long commentId, String replyText, User user) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));
        
        SocialAccount account = socialAccountRepository.findByUser(user).stream()
                .filter(a -> a.getPlatform().equalsIgnoreCase(comment.getPlatform()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No connected account for " + comment.getPlatform()));

        try {
            String token = encryptionUtils.decrypt(account.getEncryptedAccessToken());
            
            // Meta API Nuance: Instagram uses /replies, while Facebook uses /comments for replying to a comment.
            String endpoint = comment.getPlatform().equalsIgnoreCase("INSTAGRAM") ? "replies" : "comments";
            String url = String.format("https://graph.facebook.com/v21.0/%s/%s", comment.getExternalCommentId(), endpoint);
            
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("message", replyText);
            body.add("access_token", token);

            restTemplate.postForObject(url, body, JsonNode.class);
            
            logger.info("🚀 Successfully posted reply to Meta for comment {}", comment.getExternalCommentId());
            comment.setIsReplied(true);
            commentRepository.save(comment);
        } catch (Exception e) {
            logger.error("❌ Failed to send reply to Meta: {}", e.getMessage());
            throw new RuntimeException("Social Platform Reply failed: " + e.getMessage());
        }
    }

    public List<Comment> getInbox(User user, String sentiment, String priority) {
        return commentRepository.findByUserOrderByCreatedAtDesc(user);
    }
}
