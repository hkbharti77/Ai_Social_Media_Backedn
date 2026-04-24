package com.aiplatform.service;

import com.aiplatform.model.Post;
import com.aiplatform.model.PostStatus;
import com.aiplatform.model.SocialAccount;
import com.aiplatform.repository.PostRepository;
import com.aiplatform.repository.SocialAccountRepository;
import com.aiplatform.security.EncryptionUtils;
import com.aiplatform.dto.ContentGenerationDtos.CarouselResponse;
import com.aiplatform.dto.ContentGenerationDtos.CarouselSlide;
import com.aiplatform.dto.PollDtos.PollData;
import com.aiplatform.dto.PollDtos.PollOption;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PublisherService {
    private static final Logger logger = LoggerFactory.getLogger(PublisherService.class);

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private EncryptionUtils encryptionUtils;
    
    @Autowired
    private PdfService pdfService;

    @Autowired
    private S3Service s3Service;

    @Autowired
    private RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
                    
                    String token;
                    try {
                        token = encryptionUtils.decrypt(account.getEncryptedAccessToken());
                    } catch (Exception e) {
                        logger.error("❌ [Publisher] Failed to decrypt token for platform {}. Account may need to be re-linked.", platform);
                        post.setStatus(PostStatus.FAILED);
                        post.setFailureReason("Social account session expired or encryption key mismatch. Please re-link your social accounts.");
                        postRepository.save(post);
                        continue; // Skip this platform and move to next
                    }
                    
                    if (platform.equals("FACEBOOK")) {
                        if (Boolean.TRUE.equals(post.getIsStory())) {
                            publishToFacebookStory(post, account.getPageId(), token);
                        } else if (Boolean.TRUE.equals(post.getIsReel())) {
                            publishReelToFacebook(post, account.getPageId(), token);
                        } else {
                            publishToFacebook(post, account.getPageId(), token);
                        }
                        published = true;
                    } else if (platform.equals("INSTAGRAM")) {
                        if (Boolean.TRUE.equals(post.getIsStory())) {
                            publishToInstagramStory(post, account.getIgBusinessAccountId(), token);
                        } else if (Boolean.TRUE.equals(post.getIsReel())) {
                            publishReelToInstagram(post, account.getIgBusinessAccountId(), token);
                        } else {
                            publishToInstagram(post, account.getIgBusinessAccountId(), token);
                        }
                        published = true;
                    } else if (platform.equals("LINKEDIN")) {
                        if (Boolean.TRUE.equals(post.getIsPoll())) {
                            publishPollToLinkedIn(post, account.getPageId(), token);
                        } else {
                            publishToLinkedIn(post, account.getPageId(), token);
                        }
                        published = true;
                    } else if (platform.equals("X")) {
                        if (Boolean.TRUE.equals(post.getIsPoll())) {
                            publishPollToX(post, token);
                        } else {
                            publishToX(post, token);
                        }
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
        String hashtags = post.getHashtags() != null ? post.getHashtags() : "";
        String fullCaption = post.getCaption();
        if (!hashtags.isEmpty()) {
            fullCaption += "\n\n" + hashtags;
        }

        if (Boolean.TRUE.equals(post.getIsCarousel()) && post.getCarouselContent() != null) {
            try {
                CarouselResponse carousel = objectMapper.readValue(post.getCarouselContent(), CarouselResponse.class);
                List<String> mediaFbids = new ArrayList<>();

                // 1. Upload each image as unpublished
                for (CarouselSlide slide : carousel.getSlides()) {
                    String photoUrl = "https://graph.facebook.com/v19.0/" + pageId + "/photos";
                    MultiValueMap<String, String> photoBody = new LinkedMultiValueMap<>();
                    photoBody.add("url", getAccessibleUrl(slide.getImageUrl()));
                    photoBody.add("published", "false");
                    photoBody.add("access_token", accessToken);
                    
                    @SuppressWarnings("unchecked")
                    Map<String, Object> photoResp = restTemplate.postForObject(photoUrl, photoBody, Map.class);
                    if (photoResp != null && photoResp.containsKey("id")) {
                        mediaFbids.add((String) photoResp.get("id"));
                    }
                }

                // 2. Publish as a multi-photo post
                String feedUrl = "https://graph.facebook.com/v19.0/" + pageId + "/feed";
                Map<String, Object> feedBody = new HashMap<>();
                feedBody.put("message", fullCaption);
                feedBody.put("access_token", accessToken);
                
                List<Map<String, String>> attachedMedia = mediaFbids.stream()
                        .map(id -> Map.of("media_fbid", id))
                        .collect(Collectors.toList());
                feedBody.put("attached_media", attachedMedia);

                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.postForObject(feedUrl, feedBody, Map.class);
                if (response != null && response.containsKey("id")) {
                    post.setExternalPostId((String) response.get("id"));
                    logger.info("\ud83d\udcf1 Facebook Multi-Photo Post Successful: {}", post.getExternalPostId());
                } else {
                    throw new RuntimeException("Facebook Multi-Photo response missing ID.");
                }
            } catch (Exception e) {
                logger.error("Facebook Carousel Error: {}", e.getMessage());
                throw new RuntimeException("Facebook Carousel Publishing failed.", e);
            }
        } else {
            // Single Image Post (Standard)
            String url = "https://graph.facebook.com/v19.0/" + pageId + "/photos";
            
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("url", getAccessibleUrl(post.getImageUrl()));
            body.add("caption", fullCaption);
            body.add("access_token", accessToken);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);
            
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.postForObject(url, requestEntity, Map.class);
                if (response != null && response.containsKey("id")) {
                    post.setExternalPostId((String) response.get("id"));
                }
            } catch (Exception e) {
                logger.error("Facebook API error: {}", e.getMessage());
                throw e;
            }
        }
    }

    private void publishToX(Post post, String accessToken) {
        String url = "https://api.twitter.com/2/tweets";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            if (Boolean.TRUE.equals(post.getIsThread()) && post.getThreadContent() != null) {
                JsonNode threadArray = objectMapper.readTree(post.getThreadContent());
                String lastTweetId = null;

                for (JsonNode tweetNode : threadArray) {
                    Map<String, Object> body = new HashMap<>();
                    body.put("text", tweetNode.asText());
                    
                    if (lastTweetId != null) {
                        body.put("reply", Map.of("in_reply_to_tweet_id", lastTweetId));
                    }

                    HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
                    
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) response.get("data");
                    lastTweetId = (String) data.get("id");
                    
                    if (post.getExternalPostId() == null) {
                        post.setExternalPostId(lastTweetId); // Store the ID of the first tweet (hook)
                    }
                    
                    // Small delay between thread tweets to ensure order and avoid rate limits
                    Thread.sleep(1000);
                }
                logger.info("🧵 Successfully published X Thread (id={})", post.getExternalPostId());
            } else {
                // Single Tweet
                Map<String, Object> body = new HashMap<>();
                String text = post.getCaption();
                if (post.getHashtags() != null && !post.getHashtags().isEmpty()) {
                    text += "\n\n" + post.getHashtags();
                }
                body.put("text", text);

                HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
                
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                post.setExternalPostId((String) data.get("id"));
                logger.info("🐦 Successfully published single Tweet (id={})", post.getExternalPostId());
            }
        } catch (Exception e) {
            logger.error(" X/Twitter API error while publishing: {}", e.getMessage());
            throw new RuntimeException("X publishing failed: " + e.getMessage(), e);
        }
    }

    private void publishToInstagram(Post post, String igId, String accessToken) {
        if (Boolean.TRUE.equals(post.getIsCarousel()) && post.getCarouselContent() != null) {
            try {
                CarouselResponse carousel = objectMapper.readValue(post.getCarouselContent(), CarouselResponse.class);
                List<String> childIds = new ArrayList<>();

                // Step 1: Create Image Containers for each slide
                for (CarouselSlide slide : carousel.getSlides()) {
                    String containerUrl = "https://graph.facebook.com/v21.0/" + igId + "/media";
                    Map<String, Object> slideBody = new HashMap<>();
                    slideBody.put("image_url", getAccessibleUrl(slide.getImageUrl()));
                    slideBody.put("media_type", "IMAGE");
                    slideBody.put("is_carousel_item", "true");
                    slideBody.put("access_token", accessToken);

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    HttpEntity<Map<String, Object>> request = new HttpEntity<>(slideBody, headers);

                    @SuppressWarnings("unchecked")
                    Map<String, Object> slideResp = restTemplate.postForObject(containerUrl, request, Map.class);
                    if (slideResp == null || !slideResp.containsKey("id")) {
                        throw new RuntimeException("Failed to create Instagram slide container.");
                    }
                    childIds.add((String) slideResp.get("id"));
                }

                // Step 2: Create Carousel Container
                String rootContainerUrl = "https://graph.facebook.com/v21.0/" + igId + "/media";
                Map<String, Object> rootBody = new HashMap<>();
                rootBody.put("media_type", "CAROUSEL");
                rootBody.put("caption", post.getCaption() + "\n\n" + post.getHashtags());
                rootBody.put("children", String.join(",", childIds));
                rootBody.put("access_token", accessToken);

                HttpHeaders rootHeaders = new HttpHeaders();
                rootHeaders.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> rootRequest = new HttpEntity<>(rootBody, rootHeaders);

                @SuppressWarnings("unchecked")
                Map<String, Object> rootResp = restTemplate.postForObject(rootContainerUrl, rootRequest, Map.class);
                if (rootResp == null || !rootResp.containsKey("id")) {
                    throw new RuntimeException("Failed to create Instagram Carousel root container.");
                }
                String carouselId = (String) rootResp.get("id");

                // Step 3: Wait and Publish
                waitForMediaStatus(carouselId, accessToken);
                
                String publishUrl = "https://graph.facebook.com/v21.0/" + igId + "/media_publish";
                Map<String, Object> publishBody = new HashMap<>();
                publishBody.put("creation_id", carouselId);
                publishBody.put("access_token", accessToken);

                HttpHeaders publishHeaders = new HttpHeaders();
                publishHeaders.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> publishRequest = new HttpEntity<>(publishBody, publishHeaders);

                @SuppressWarnings("unchecked")
                Map<String, Object> finalResp = restTemplate.postForObject(publishUrl, publishRequest, Map.class);
                post.setExternalPostId((String) finalResp.get("id"));
                logger.info("🚡 Instagram Carousel Successful: {}", post.getExternalPostId());

            } catch (Exception e) {
                logger.error("Instagram Carousel Error: {}", e.getMessage());
                throw new RuntimeException("Instagram Carousel Publish failed.", e);
            }
        } else {
            // STEP 1: Create media container
            String containerUrl = UriComponentsBuilder.fromHttpUrl("https://graph.facebook.com/v21.0/" + igId + "/media")
                    .queryParam("image_url", getAccessibleUrl(post.getImageUrl()))
                    .queryParam("caption", post.getCaption() + (post.getHashtags() != null ? "\n\n" + post.getHashtags() : ""))
                    .queryParam("access_token", accessToken)
                    .toUriString();

            Map<String, Object> containerResponse = executeMetaCallWithRetry(() -> 
                restTemplate.postForObject(containerUrl, null, Map.class));
                
            String creationId = (String) containerResponse.get("id");
            
            if (creationId == null) {
                throw new RuntimeException("Failed to create Instagram media container.");
            }

            // STEP 2: Poll status
            waitForMediaStatus(creationId, accessToken);

            // STEP 3: Publish media
            String publishUrl = UriComponentsBuilder.fromHttpUrl("https://graph.facebook.com/v21.0/" + igId + "/media_publish")
                    .queryParam("creation_id", creationId)
                    .queryParam("access_token", accessToken)
                    .toUriString();

            Map<String, Object> publishResponse = executeMetaCallWithRetry(() -> 
                restTemplate.postForObject(publishUrl, null, Map.class));
            post.setExternalPostId((String) publishResponse.get("id"));
        }
    }

    private void waitForMediaStatus(String creationId, String accessToken) {
        boolean isReady = false;
        int retries = 0;
        int maxRetries = 12; // 60 seconds
        
        while (!isReady && retries < maxRetries) {
            try {
                Thread.sleep(5000);
                retries++;
                
                String statusUrl = "https://graph.facebook.com/v21.0/" + creationId + 
                                  "?fields=status_code&access_token=" + accessToken;
                @SuppressWarnings("unchecked")
                Map<String, Object> statusResponse = restTemplate.getForObject(statusUrl, Map.class);
                String statusCode = statusResponse != null ? (String) statusResponse.get("status_code") : "UNKNOWN";
                
                logger.info("\ud83d\udcf8 [Polling] Media {} Status: {}", creationId, statusCode);
                
                if ("FINISHED".equalsIgnoreCase(statusCode)) {
                    isReady = true;
                } else if ("ERROR".equalsIgnoreCase(statusCode)) {
                    throw new RuntimeException("Media processing failed.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Polling interrupted");
            }
        }
        if (!isReady) throw new RuntimeException("Media timed out.");
    }

    private void publishToLinkedIn(Post post, String personUrn, String accessToken) {
        String hashtags = post.getHashtags() != null ? post.getHashtags() : "";
        String fullCaption = post.getCaption();
        if (!hashtags.isEmpty()) {
            fullCaption += "\n\n" + hashtags;
        }

        if (Boolean.TRUE.equals(post.getIsCarousel()) && post.getCarouselContent() != null) {
            publishLinkedInCarousel(post, personUrn, accessToken);
        } else {
            // Standard Single Image / Text Post
            String imageUrn = null;
            if (post.getImageUrl() != null && !post.getImageUrl().isEmpty()) {
                imageUrn = uploadImageToLinkedIn(post.getImageUrl(), personUrn, accessToken);
            }

            Map<String, Object> body = new HashMap<>();
            body.put("author", personUrn);
            body.put("commentary", fullCaption);
            body.put("visibility", "PUBLIC");
            body.put("distribution", Map.of("feedDistribution", "MAIN_FEED"));
            body.put("lifecycleState", "PUBLISHED");

            if (imageUrn != null) {
                body.put("content", Map.of("media", Map.of("id", imageUrn, "altText", "AI Post")));
            }

            sendLinkedInPost(body, accessToken, post);
        }
    }

    private void publishLinkedInCarousel(Post post, String personUrn, String accessToken) {
        try {
            CarouselResponse carousel = objectMapper.readValue(post.getCarouselContent(), CarouselResponse.class);
            List<String> imageUrls = carousel.getSlides().stream().map(CarouselSlide::getImageUrl).collect(Collectors.toList());

            // 1. Generate PDF
            byte[] pdfBytes = pdfService.generateCarouselPdf(imageUrls);

            // 2. Initialize Upload
            String initUrl = "https://api.linkedin.com/v2/documents?action=initializeUpload";
            Map<String, Object> initRequest = Map.of("initializeUploadRequest", Map.of("owner", personUrn));
            
            HttpHeaders headers = getLinkedInHeaders(accessToken);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(initRequest, headers);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> initResponse = restTemplate.postForObject(initUrl, entity, Map.class);
            Map<String, Object> value = (Map<String, Object>) initResponse.get("value");
            String uploadUrl = (String) value.get("uploadUrl");
            String documentUrn = (String) value.get("document");

            // 3. Upload bytes
            HttpHeaders uploadHeaders = new HttpHeaders();
            uploadHeaders.setBearerAuth(accessToken);
            uploadHeaders.setContentType(MediaType.APPLICATION_PDF);
            HttpEntity<byte[]> uploadEntity = new HttpEntity<>(pdfBytes, uploadHeaders);
            restTemplate.put(uploadUrl, uploadEntity);

            // 4. Publish
            Map<String, Object> body = new HashMap<>();
            body.put("author", personUrn);
            body.put("commentary", post.getCaption());
            body.put("visibility", "PUBLIC");
            body.put("distribution", Map.of("feedDistribution", "MAIN_FEED"));
            body.put("lifecycleState", "PUBLISHED");
            // Correct format for LinkedIn Documents in 202401
            body.put("content", Map.of("media", Map.of("id", documentUrn, "title", "AI Carousel")));

            sendLinkedInPost(body, accessToken, post);
            logger.info("\ud83d\udcb1 LinkedIn Document Carousel Successful: {}", post.getExternalPostId());
        } catch (Exception e) {
            logger.error("LinkedIn Carousel Error: {}", e.getMessage());
            throw new RuntimeException("LinkedIn Carousel Publishing failed.");
        }
    }

    private HttpHeaders getLinkedInHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        headers.set("LinkedIn-Version", "202401");
        headers.set("X-Restli-Protocol-Version", "2.0.0");
        return headers;
    }

    private void sendLinkedInPost(Map<String, Object> body, String accessToken, Post post) {
        String url = "https://api.linkedin.com/v2/posts";
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, getLinkedInHeaders(accessToken));
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
        if (response != null && response.containsKey("id")) {
            post.setExternalPostId((String) response.get("id"));
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

    private void publishToInstagramStory(Post post, String igId, String accessToken) {
        try {
            // Step 1: Create Stories Media Container
            String containerUrl = "https://graph.facebook.com/v21.0/" + igId + "/media";
            Map<String, Object> body = new HashMap<>();
            body.put("image_url", getAccessibleUrl(post.getImageUrl()));
            body.put("media_type", "STORIES");
            body.put("access_token", accessToken);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> containerResponse = restTemplate.postForObject(containerUrl, request, Map.class);
            String creationId = (String) containerResponse.get("id");

            if (creationId == null) throw new RuntimeException("Failed to create IG Story container.");

            waitForMediaStatus(creationId, accessToken);

            // Step 2: Publish
            String publishUrl = "https://graph.facebook.com/v21.0/" + igId + "/media_publish";
            Map<String, Object> publishBody = new HashMap<>();
            publishBody.put("creation_id", creationId);
            publishBody.put("access_token", accessToken);

            HttpEntity<Map<String, Object>> publishRequest = new HttpEntity<>(publishBody, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> publishResponse = restTemplate.postForObject(publishUrl, publishRequest, Map.class);
            post.setExternalPostId((String) publishResponse.get("id"));
            logger.info("📸 Instagram Story Published successfully: {}", post.getExternalPostId());
        } catch (Exception e) {
            logger.error("Instagram Story Error: {}", e.getMessage());
            throw new RuntimeException("Instagram Story publishing failed", e);
        }
    }

    private void publishToFacebookStory(Post post, String pageId, String accessToken) {
        try {
            // Step 1: Upload image or video as unpublished
            if (post.getImageUrl() == null && post.getVideoUrl() != null) {
                 // Logic for FB Stories Video if needed...
                 // Fallback to Image for now as standard implementation
            }

            String uploadUrl = "https://graph.facebook.com/v21.0/" + pageId + "/photos";
            MultiValueMap<String, Object> uploadBody = new LinkedMultiValueMap<>();
            uploadBody.add("url", getAccessibleUrl(post.getImageUrl()));
            uploadBody.add("published", "false");
            uploadBody.add("access_token", accessToken);

            @SuppressWarnings("unchecked")
            Map<String, Object> uploadResp = restTemplate.postForObject(uploadUrl, uploadBody, Map.class);
            String photoId = (String) uploadResp.get("id");

            if (photoId == null) throw new RuntimeException("Failed to upload FB story photo.");

            // Step 2: Publish to Story
            String storyUrl = "https://graph.facebook.com/v21.0/" + pageId + "/photo_stories";
            MultiValueMap<String, Object> storyBody = new LinkedMultiValueMap<>();
            storyBody.add("photo_id", photoId);
            storyBody.add("access_token", accessToken);

            @SuppressWarnings("unchecked")
            Map<String, Object> storyResp = restTemplate.postForObject(storyUrl, storyBody, Map.class);
            post.setExternalPostId((String) storyResp.get("id"));
            logger.info("📖 Facebook Story Published successfully: {}", post.getExternalPostId());
        } catch (Exception e) {
            logger.error("Facebook Story Error: {}", e.getMessage());
            throw new RuntimeException("Facebook Story publishing failed", e);
        }
    }

    private void publishReelToInstagram(Post post, String igId, String accessToken) {
        try {
            // For Instagram Reels, we need a video_url. If it's missing, fail early.
            if (post.getVideoUrl() == null || post.getVideoUrl().isEmpty()) {
                throw new RuntimeException("Video URL is required to publish an Instagram Reel.");
            }

            // Step 1: Create Media Container for REELS
            String containerUrl = "https://graph.facebook.com/v21.0/" + igId + "/media";
            Map<String, Object> body = new HashMap<>();
            body.put("video_url", post.getVideoUrl());
            body.put("media_type", "REELS");
            body.put("caption", post.getCaption() + (post.getHashtags() != null ? "\n\n" + post.getHashtags() : ""));
            body.put("access_token", accessToken);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> containerResponse = restTemplate.postForObject(containerUrl, request, Map.class);
            String creationId = (String) containerResponse.get("id");

            if (creationId == null) throw new RuntimeException("Failed to create IG Reels container.");

            // Step 2: Wait for video to process
            waitForMediaStatus(creationId, accessToken);

            // Step 3: Publish
            String publishUrl = "https://graph.facebook.com/v21.0/" + igId + "/media_publish";
            Map<String, Object> publishBody = new HashMap<>();
            publishBody.put("creation_id", creationId);
            publishBody.put("access_token", accessToken);

            HttpEntity<Map<String, Object>> publishRequest = new HttpEntity<>(publishBody, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> publishResponse = restTemplate.postForObject(publishUrl, publishRequest, Map.class);
            post.setExternalPostId((String) publishResponse.get("id"));
            logger.info("🎥 Instagram Reel Published successfully: {}", post.getExternalPostId());
        } catch (Exception e) {
            logger.error("Instagram Reel Error: {}", e.getMessage());
            throw new RuntimeException("Instagram Reel publishing failed", e);
        }
    }

    private void publishReelToFacebook(Post post, String pageId, String accessToken) {
        try {
            if (post.getVideoUrl() == null || post.getVideoUrl().isEmpty()) {
                throw new RuntimeException("Video URL is required to publish a Facebook Reel.");
            }

            // Facebook Page Reels endpoint (using Video API)
            String uploadUrl = "https://graph.facebook.com/v21.0/" + pageId + "/video_reels";
            
            // Initialization Phase
            MultiValueMap<String, Object> initBody = new LinkedMultiValueMap<>();
            initBody.add("upload_phase", "start");
            initBody.add("access_token", accessToken);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> initResp = restTemplate.postForObject(uploadUrl, initBody, Map.class);
            if (initResp == null || !initResp.containsKey("video_id")) {
                throw new RuntimeException("Failed to initialize Facebook Reel upload.");
            }
            String videoId = (String) initResp.get("video_id");

            // For external URLs passing via API might require special handling or direct transfer, 
            // Graph API supports file_url directly for videos in standard feed, but for reels, 
            // usually you provide file_url with the finish phase or standard video upload.
            // Using standard video endpoint as fallback for pages since video_reels has complex chunking.
            String fallbackUrl = "https://graph.facebook.com/v21.0/" + pageId + "/videos";
            MultiValueMap<String, Object> videoBody = new LinkedMultiValueMap<>();
            videoBody.add("file_url", post.getVideoUrl());
            videoBody.add("description", post.getCaption() + (post.getHashtags() != null ? "\n\n" + post.getHashtags() : ""));
            videoBody.add("access_token", accessToken);

            @SuppressWarnings("unchecked")
            Map<String, Object> videoResp = restTemplate.postForObject(fallbackUrl, videoBody, Map.class);
            post.setExternalPostId((String) videoResp.get("id"));
            logger.info("🎬 Facebook Reel/Video Published successfully: {}", post.getExternalPostId());
            
        } catch (Exception e) {
            logger.error("Facebook Reel Error: {}", e.getMessage());
            throw new RuntimeException("Facebook Reel publishing failed", e);
        }
    }

    private void publishPollToLinkedIn(Post post, String personUrn, String accessToken) {
        try {
            PollData pollData = objectMapper.readValue(post.getPollContent(), PollData.class);
            Map<String, Object> body = new HashMap<>();
            body.put("author", personUrn);
            body.put("commentary", post.getCaption());
            body.put("visibility", "PUBLIC");
            body.put("distribution", Map.of("feedDistribution", "MAIN_FEED"));
            body.put("lifecycleState", "PUBLISHED");

            Map<String, Object> poll = new HashMap<>();
            poll.put("question", pollData.getQuestion());
            poll.put("options", pollData.getOptions().stream()
                    .map(o -> Map.of("text", o.getText()))
                    .collect(Collectors.toList()));
            
            // LinkedIn duration settings
            String duration = "ONE_DAY";
            if (pollData.getDurationMinutes() > 1440) duration = "THREE_DAYS";
            if (pollData.getDurationMinutes() > 4320) duration = "SEVEN_DAYS";
            if (pollData.getDurationMinutes() > 10080) duration = "TWO_WEEKS";
            poll.put("settings", Map.of("duration", duration));

            body.put("content", Map.of("poll", poll));

            sendLinkedInPost(body, accessToken, post);
            logger.info("📊 LinkedIn Poll Published successfully: {}", post.getExternalPostId());
        } catch (Exception e) {
            logger.error("LinkedIn Poll Error: {}", e.getMessage());
            throw new RuntimeException("LinkedIn Poll publishing failed", e);
        }
    }

    private void publishPollToX(Post post, String accessToken) {
        String url = "https://api.twitter.com/2/tweets";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            PollData pollData = objectMapper.readValue(post.getPollContent(), PollData.class);
            Map<String, Object> body = new HashMap<>();
            body.put("text", post.getCaption());
            
            Map<String, Object> poll = new HashMap<>();
            poll.put("options", pollData.getOptions().stream()
                    .map(PollOption::getText)
                    .collect(Collectors.toList()));
            poll.put("duration_minutes", pollData.getDurationMinutes());
            
            body.put("poll", poll);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            post.setExternalPostId((String) data.get("id"));
            logger.info("📊 X Poll Published successfully: {}", post.getExternalPostId());
        } catch (Exception e) {
            logger.error("X Poll Error: {}", e.getMessage());
            throw new RuntimeException("X Poll publishing failed", e);
        }
    }

    /**
     * Resolves an S3 permanent URL to a temporary Presigned URL.
     * This ensures Meta's crawlers can fetch the image even if the bucket is restricted.
     */
    private String getAccessibleUrl(String imageUrl) {
        // We revert to returning the original URL because Meta (Instagram/Facebook) 
        // often performs a HEAD request before a GET. Presigned URLs for GET will fail HEAD requests.
        // User's S3 bucket is configured for public read of these media assets.
        return imageUrl;
    }

    private <T> T executeMetaCallWithRetry(java.util.function.Supplier<T> call) {
        int maxRetries = 2;
        int attempt = 0;
        while (attempt <= maxRetries) {
            try {
                return call.get();
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                if (attempt < maxRetries && (msg.contains("connection reset") || msg.contains("i/o error") || msg.contains("timeout"))) {
                    attempt++;
                    logger.warn("⚠️ Meta API Network Error ({}). Retrying in 2s... [Attempt {}]", msg, attempt);
                    throttle(2000);
                    continue;
                }
                throw e;
            }
        }
        return null;
    }

    private void throttle(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
