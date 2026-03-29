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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FacebookReviewService {
    private static final Logger logger = LoggerFactory.getLogger(FacebookReviewService.class);

    private final SocialAccountRepository socialAccountRepository;
    private final BusinessProfileRepository businessProfileRepository;
    private final EncryptionUtils encryptionUtils;
    private final AiContentService aiContentService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<ReviewData> getPageReviews(User user) {
        SocialAccount fbAccount = getFacebookAccount(user);
        String accessToken = decryptToken(fbAccount.getEncryptedAccessToken());

        String url = String.format("https://graph.facebook.com/v21.0/%s/ratings?access_token=%s",
                fbAccount.getPageId(), accessToken);

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.path("data");

            List<ReviewData> reviews = new ArrayList<>();
            for (JsonNode node : data) {
                reviews.add(ReviewData.builder()
                        .id(node.path("open_graph_story").path("id").asText()) // Using story ID as review ID
                        .reviewerName(node.path("reviewer").path("name").asText())
                        .rating(node.path("rating").asInt())
                        .reviewText(node.path("review_text").asText())
                        .createdTime(node.path("created_time").asText())
                        .build());
            }
            return reviews;
        } catch (Exception e) {
            logger.error("Failed to fetch FB reviews: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public String generateAiReply(User user, String reviewText, int rating) {
        BusinessProfile bp = businessProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Business Profile not found"));
        
        return aiContentService.generateReviewReply(bp.getBusinessName(), reviewText, rating);
    }

    public void postReply(User user, String reviewId, String replyText) {
        SocialAccount fbAccount = getFacebookAccount(user);
        String accessToken = decryptToken(fbAccount.getEncryptedAccessToken());

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

    private SocialAccount getFacebookAccount(User user) {
        return socialAccountRepository.findByUserAndPlatform(user, "FACEBOOK")
                .stream().findFirst()
                .orElseThrow(() -> new RuntimeException("No connected Facebook Page found"));
    }

    private String decryptToken(String encryptedToken) {
        try {
            return encryptionUtils.decrypt(encryptedToken);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt access token");
        }
    }
}
