package com.aiplatform.service;

import com.aiplatform.dto.FacebookReviewDtos.ReviewData;
import com.aiplatform.model.BusinessProfile;
import com.aiplatform.model.SocialAccount;
import com.aiplatform.model.User;
import com.aiplatform.repository.BusinessProfileRepository;
import com.aiplatform.repository.SocialAccountRepository;
import com.aiplatform.security.EncryptionUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for handling Facebook Page reviews and AI-generated replies.
 */
@Service
public class FacebookReviewService {
    private static final Logger logger = LoggerFactory.getLogger(FacebookReviewService.class);

    private final SocialAccountRepository socialAccountRepository;
    private final BusinessProfileRepository businessProfileRepository;
    private final EncryptionUtils encryptionUtils;
    private final AiEngagementService aiEngagementService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FacebookReviewService(
            SocialAccountRepository socialAccountRepository,
            BusinessProfileRepository businessProfileRepository,
            EncryptionUtils encryptionUtils,
            AiEngagementService aiEngagementService,
            RestTemplate restTemplate) {
        this.socialAccountRepository = socialAccountRepository;
        this.businessProfileRepository = businessProfileRepository;
        this.encryptionUtils = encryptionUtils;
        this.aiEngagementService = aiEngagementService;
        this.restTemplate = restTemplate;
    }

    /**
     * Fetches reviews for the user's connected Facebook page.
     * Returns an empty list if no account is connected instead of throwing an error.
     */
    public List<ReviewData> getPageReviews(User user) {
        SocialAccount fbAccount = resolveFacebookAccount(user).orElse(null);
        if (fbAccount == null) {
            logger.info("Skipping review fetch: No connected Facebook page found for user id={}", user.getId());
            return new ArrayList<>();
        }

        String accessToken = decryptToken(fbAccount.getEncryptedAccessToken());
        if (accessToken == null) return new ArrayList<>();

        String url = String.format("https://graph.facebook.com/v21.0/%s/ratings?fields=id,reviewer,rating,review_text,created_time&access_token=%s",
                fbAccount.getPageId(), accessToken);

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.path("data");

            List<ReviewData> reviews = new ArrayList<>();
            for (JsonNode node : data) {
                reviews.add(ReviewData.builder()
                        .id(node.path("id").asText())
                        .reviewerName(node.path("reviewer").path("name").asText())
                        .rating(node.path("rating").asInt())
                        .reviewText(node.path("review_text").asText())
                        .createdTime(node.path("created_time").asText())
                        .build());
            }
            return reviews;
        } catch (Exception e) {
            logger.error("Failed to fetch FB reviews for page {}: {}", fbAccount.getPageId(), e.getMessage());
            return new ArrayList<>();
        }
    }

    public String generateAiReply(User user, String reviewText, int rating) {
        BusinessProfile bp = businessProfileRepository.findAllByUser(user)
                .stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Business Profile not found"));
        
        return aiEngagementService.generateReviewReply(bp.getBusinessName(), reviewText, rating, user.getId());
    }

    public void postReply(User user, String reviewId, String replyText) {
        SocialAccount fbAccount = resolveFacebookAccount(user)
                .orElseThrow(() -> new RuntimeException("Cannot post reply: No connected Facebook Page found"));
        
        String accessToken = decryptToken(fbAccount.getEncryptedAccessToken());
        if (accessToken == null) {
            throw new RuntimeException("Failed to post reply: Access token decryption failed.");
        }

        // Note: Graph API uses comments on the review story ID to "reply"
        String url = String.format("https://graph.facebook.com/v21.0/%s/comments?message=%s&access_token=%s",
                reviewId, replyText, accessToken);

        try {
            restTemplate.postForEntity(url, null, String.class);
        } catch (Exception e) {
            logger.error("Failed to post FB review reply: {}", e.getMessage());
            throw new RuntimeException("Failed to post reply to Facebook.");
        }
    }

    private Optional<SocialAccount> resolveFacebookAccount(User user) {
        return socialAccountRepository.findByUserAndPlatform(user, "FACEBOOK")
                .stream().findFirst();
    }

    private String decryptToken(String encryptedToken) {
        if (encryptedToken == null) return null;
        try {
            return encryptionUtils.decrypt(encryptedToken);
        } catch (Exception e) {
            logger.error("[FacebookReview] Failed to decrypt access token. The encryption key might have changed. Please re-link account.");
            return null;
        }
    }
}
