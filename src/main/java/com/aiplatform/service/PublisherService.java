package com.aiplatform.service;

import com.aiplatform.model.Post;
import com.aiplatform.model.PostStatus;
import com.aiplatform.model.SocialAccount;
import com.aiplatform.repository.PostRepository;
import com.aiplatform.repository.SocialAccountRepository;
import com.aiplatform.security.EncryptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PublisherService {
    private static final Logger logger = LoggerFactory.getLogger(PublisherService.class);

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private EncryptionUtils encryptionUtils;

    private final RestTemplate restTemplate = new RestTemplate();

    @Async
    public void publishPost(Post post) {
        try {
            List<SocialAccount> accounts = socialAccountRepository.findByUser(post.getUser());
            
            boolean published = false;
            for (SocialAccount account : accounts) {
                String platform = account.getPlatform().toUpperCase();
                
                // Match platform: if post is for "ALL", or matches specific platform
                if (post.getPlatform().equalsIgnoreCase("ALL") || 
                    post.getPlatform().equalsIgnoreCase("BOTH") || // legacy
                    post.getPlatform().equalsIgnoreCase(platform)) {
                    
                    String token = encryptionUtils.decrypt(account.getEncryptedAccessToken());
                    
                    if (platform.equals("FACEBOOK")) {
                        publishToFacebook(post, account.getPageId(), token);
                        published = true;
                    } else if (platform.equals("INSTAGRAM")) {
                        publishToInstagram(post, account.getIgBusinessAccountId(), token);
                        published = true;
                    } else if (platform.equals("LINKEDIN")) {
                        publishToLinkedIn(post, account.getPageId(), token);
                        published = true;
                    }
                }
            }

            if (published) {
                post.setStatus(PostStatus.PUBLISHED);
                post.setPublishedAt(LocalDateTime.now());
            } else {
                post.setStatus(PostStatus.FAILED);
                post.setFailureReason("No connected social accounts found for the specified platform.");
            }
        } catch (Exception e) {
            logger.error("Failed to publish post {}: {}", post.getId(), e.getMessage());
            post.setStatus(PostStatus.FAILED);
            post.setFailureReason(e.getMessage());
        } finally {
            postRepository.save(post);
        }
    }

    private void publishToFacebook(Post post, String pageId, String accessToken) {
        String url = "https://graph.facebook.com/v19.0/" + pageId + "/photos";
        
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("url", post.getImageUrl());
        
        String hashtags = post.getHashtags() != null ? post.getHashtags() : "";
        String fullCaption = post.getCaption();
        if (!hashtags.isEmpty()) {
            fullCaption += "\n\n" + hashtags;
        }
        body.add("caption", fullCaption);
        body.add("access_token", accessToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, requestEntity, Map.class);
            logger.info("Facebook API Response: {}", response);
            
            if (response != null && response.containsKey("id")) {
                post.setExternalPostId((String) response.get("id"));
            } else {
                throw new RuntimeException("Facebook response missing 'id': " + response);
            }
        } catch (Exception e) {
            logger.error("Facebook API error while publishing: {}", e.getMessage());
            throw e;
        }
    }

    private void publishToInstagram(Post post, String igId, String accessToken) {
        // Step 1: Create media container
        String containerUrl = "https://graph.facebook.com/v19.0/" + igId + "/media";
        
        MultiValueMap<String, Object> containerBody = new LinkedMultiValueMap<>();
        containerBody.add("image_url", post.getImageUrl());
        containerBody.add("caption", post.getCaption() + "\n\n" + post.getHashtags());
        containerBody.add("access_token", accessToken);


        @SuppressWarnings("unchecked")
        Map<String, Object> containerResponse = restTemplate.postForObject(containerUrl, containerBody, Map.class);
        String creationId = (String) containerResponse.get("id");
        
        if (creationId == null) {
            throw new RuntimeException("Failed to create Instagram media container. Response: " + containerResponse);
        }

        // Step 2: Poll for "FINISHED" status
        // Images usually take 5-30 seconds to be ready for publishing
        boolean isReady = false;
        int retries = 0;
        int maxRetries = 10; // 10 * 5 seconds = 50 seconds max wait
        
        while (!isReady && retries < maxRetries) {
            try {
                Thread.sleep(5000); // Wait 5 seconds
                retries++;
                
                String statusUrl = "https://graph.facebook.com/v19.0/" + creationId + 
                                  "?fields=status_code&access_token=" + accessToken;
                @SuppressWarnings("unchecked")
                Map<String, Object> statusResponse = restTemplate.getForObject(statusUrl, Map.class);
                String statusCode = (String) statusResponse.get("status_code");
                
                logger.info("\ud83d\udcf8 [Instagram] Polling media {} - Status: {} (Attempt {})", creationId, statusCode, retries);
                
                if ("FINISHED".equalsIgnoreCase(statusCode)) {
                    isReady = true;
                } else if ("ERROR".equalsIgnoreCase(statusCode)) {
                    throw new RuntimeException("Instagram media processing failed: " + statusResponse.get("status_message"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Instagram polling interrupted", e);
            }
        }

        if (!isReady) {
            throw new RuntimeException("Instagram media timed out after " + (maxRetries * 5) + " seconds.");
        }

        // Step 3: Publish media
        String publishUrl = "https://graph.facebook.com/v19.0/" + igId + "/media_publish";
        MultiValueMap<String, Object> publishBody = new LinkedMultiValueMap<>();
        publishBody.add("creation_id", creationId);
        publishBody.add("access_token", accessToken);

        @SuppressWarnings("unchecked")
        Map<String, Object> publishResponse = restTemplate.postForObject(publishUrl, publishBody, Map.class);
        
        if (publishResponse != null && publishResponse.containsKey("id")) {
            post.setExternalPostId((String) publishResponse.get("id"));
            logger.info("\u2705 [Instagram] Successfully published post ID: {}", post.getExternalPostId());
        } else {
            throw new RuntimeException("Failed to finalize Instagram publish. Response: " + publishResponse);
        }
    }

    private void publishToLinkedIn(Post post, String personUrn, String accessToken) {
        String url = "https://api.linkedin.com/v2/posts";
        
        // Prepare the caption with hashtags
        String hashtags = post.getHashtags() != null ? post.getHashtags() : "";
        String fullCaption = post.getCaption();
        if (!hashtags.isEmpty()) {
            fullCaption += "\n\n" + hashtags;
        }

        String imageUrn = null;
        if (post.getImageUrl() != null && !post.getImageUrl().isEmpty()) {
            try {
                imageUrn = uploadImageToLinkedIn(post.getImageUrl(), personUrn, accessToken);
                logger.info("\u2705 [LinkedIn] Image uploaded successfully: {}", imageUrn);
            } catch (Exception e) {
                logger.error("\u274c [LinkedIn] Image upload failed, falling back to text-only: {}", e.getMessage());
            }
        }

        // LinkedIn 2024-01 Versionized API Body
        Map<String, Object> body = new HashMap<>();
        body.put("author", personUrn);
        body.put("commentary", fullCaption);
        body.put("visibility", "PUBLIC");
        body.put("distribution", Map.of("feedDistribution", "MAIN_FEED"));
        body.put("lifecycleState", "PUBLISHED");
        body.put("isReshareDisabledByAuthor", false);
        
        if (imageUrn != null) {
            body.put("content", Map.of(
                "media", Map.of(
                    "id", imageUrn,
                    "altText", "AI Social Post"
                )
            ));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        headers.set("LinkedIn-Version", "202401");
        headers.set("X-Restli-Protocol-Version", "2.0.0");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
            logger.info("\u2705 [LinkedIn] Successfully published post to URN: {}", personUrn);
            
            if (response != null && response.containsKey("id")) {
                post.setExternalPostId((String) response.get("id"));
            }
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            String errorBody = e.getResponseBodyAsString();
            logger.error("\u274c [LinkedIn] Final publish failed ({}) - Body: {}", e.getStatusCode(), errorBody);
            throw new RuntimeException("LinkedIn publishing failed: " + errorBody, e);
        } catch (Exception e) {
            logger.error("\u274c [LinkedIn] Final publish error: {}", e.getMessage());
            throw new RuntimeException("LinkedIn publishing failed: " + e.getMessage(), e);
        }
    }

    private String uploadImageToLinkedIn(String imageUrl, String personUrn, String accessToken) {
        // Step 1: Initialize Upload
        String initUrl = "https://api.linkedin.com/v2/images?action=initializeUpload";
        
        Map<String, Object> initRequest = Map.of(
            "initializeUploadRequest", Map.of(
                "owner", personUrn
            )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("LinkedIn-Version", "202401");
        headers.set("X-Restli-Protocol-Version", "2.0.0");
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(initRequest, headers);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> initResponse = restTemplate.postForObject(initUrl, entity, Map.class);
        
        if (initResponse == null || !initResponse.containsKey("value")) {
            throw new RuntimeException("Failed to initialize LinkedIn image upload.");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) initResponse.get("value");
        String uploadUrl = (String) value.get("uploadUrl");
        String imageUrn = (String) value.get("image");

        // Step 2: Download image from S3
        byte[] imageBytes = restTemplate.getForObject(imageUrl, byte[].class);
        if (imageBytes == null) {
            throw new RuntimeException("Could not download image from URL: " + imageUrl);
        }

        // Step 3: PUT image to LinkedIn
        HttpHeaders uploadHeaders = new HttpHeaders();
        uploadHeaders.setBearerAuth(accessToken);
        uploadHeaders.setContentType(MediaType.IMAGE_PNG); // S3 URL usually suggests extensions, but PNG is safe for LinkedIn

        HttpEntity<byte[]> uploadEntity = new HttpEntity<>(imageBytes, uploadHeaders);
        restTemplate.put(uploadUrl, uploadEntity);

        return imageUrn;
    }
}
