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
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${fb.app.id}")
    private String fbAppId;

    @Value("${app.instagram.reel.skip-transcode:false}")
    private boolean skipReelTranscode;

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
    private VideoTranscodeService videoTranscodeService;

    @Autowired
    private RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ─────────────────────────────────────────────────
    // Runtime Configuration (for testing/debugging)
    // ─────────────────────────────────────────────────

    /**
     * Get current transcoding bypass status
     */
    public boolean isSkipReelTranscode() {
        return skipReelTranscode;
    }

    /**
     * Set transcoding bypass at runtime (for testing)
     * WARNING: This is for debugging only!
     */
    public void setSkipReelTranscode(boolean skipReelTranscode) {
        this.skipReelTranscode = skipReelTranscode;
        logger.warn("🔧 [DEBUG] Reel transcoding bypass changed to: {}", skipReelTranscode);
    }

    // ─────────────────────────────────────────────────
    // Main Publishing Logic
    // ─────────────────────────────────────────────────

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
                            // Validate story has required media
                            if (post.getImageUrl() == null || post.getImageUrl().trim().isEmpty()) {
                                throw new RuntimeException("Facebook Story requires an image. Please add an image to this post.");
                            }
                            publishToFacebookStory(post, account.getPageId(), token);
                        } else if (Boolean.TRUE.equals(post.getIsReel())) {
                            publishReelToFacebook(post, account.getPageId(), token);
                        } else {
                            publishToFacebook(post, account.getPageId(), token);
                        }
                        published = true;
                    } else if (platform.equals("INSTAGRAM")) {
                        if (Boolean.TRUE.equals(post.getIsStory())) {
                            // Validate story has required media
                            if (post.getImageUrl() == null || post.getImageUrl().trim().isEmpty()) {
                                throw new RuntimeException("Instagram Story requires an image. Please add an image to this post.");
                            }
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
                    // Build URL with access_token only in query params
                    String photoUrl = UriComponentsBuilder
                            .fromHttpUrl("https://graph.facebook.com/v19.0/" + pageId + "/photos")
                            .queryParam("access_token", accessToken)
                            .toUriString();
                    
                    // Send image URL and published flag in the body
                    MultiValueMap<String, String> photoBody = new LinkedMultiValueMap<>();
                    photoBody.add("url", getAccessibleUrl(slide.getImageUrl()));
                    photoBody.add("published", "false");
                    
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(photoBody, headers);
                    
                    @SuppressWarnings("unchecked")
                    Map<String, Object> photoResp = restTemplate.postForObject(photoUrl, request, Map.class);
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

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> request = new HttpEntity<>(feedBody, headers);

                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.postForObject(feedUrl, request, Map.class);
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
            // Build URL with access_token only in query params
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://graph.facebook.com/v19.0/" + pageId + "/photos")
                    .queryParam("access_token", accessToken)
                    .toUriString();
            
            // Send image URL and caption in the body to avoid encoding issues
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("url", getAccessibleUrl(post.getImageUrl()));
            body.add("caption", fullCaption);

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

    @SuppressWarnings("unchecked")
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
                Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
                
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                post.setExternalPostId((String) data.get("id"));
                logger.info("🐦 Successfully published single Tweet (id={})", post.getExternalPostId());
            }
        } catch (Exception e) {
            logger.error(" X/Twitter API error while publishing: {}", e.getMessage());
            throw new RuntimeException("X publishing failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void publishToInstagram(Post post, String igId, String accessToken) {
        if (Boolean.TRUE.equals(post.getIsCarousel()) && post.getCarouselContent() != null) {
            try {
                CarouselResponse carousel = objectMapper.readValue(post.getCarouselContent(), CarouselResponse.class);
                List<String> childIds = new ArrayList<>();

                // Step 1: Create Image Containers for each slide
                for (CarouselSlide slide : carousel.getSlides()) {
                    // Build URL with only access_token in query params
                    String containerUrl = UriComponentsBuilder
                            .fromHttpUrl("https://graph.facebook.com/v21.0/" + igId + "/media")
                            .queryParam("access_token", accessToken)
                            .toUriString();

                    // Send image_url and other params in the body to avoid encoding issues
                    MultiValueMap<String, String> slideBody = new LinkedMultiValueMap<>();
                    slideBody.add("image_url", getAccessibleUrlForInstagramPhoto(slide.getImageUrl()));
                    slideBody.add("media_type", "IMAGE");
                    slideBody.add("is_carousel_item", "true");

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(slideBody, headers);

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

                Map<String, Object> finalResp = restTemplate.postForObject(publishUrl, publishRequest, Map.class);
                post.setExternalPostId((String) finalResp.get("id"));
                logger.info("🚡 Instagram Carousel Successful: {}", post.getExternalPostId());

            } catch (Exception e) {
                logger.error("Instagram Carousel Error: {}", e.getMessage());
                throw new RuntimeException("Instagram Carousel Publish failed.", e);
            }
        } else {
            // STEP 1: Create media container
            // Send image_url in the body to avoid double-encoding issues with presigned URLs
            String containerUrl = UriComponentsBuilder
                    .fromHttpUrl("https://graph.facebook.com/v21.0/" + igId + "/media")
                    .queryParam("access_token", accessToken)
                    .toUriString();
            
            // Send all parameters in the body
            MultiValueMap<String, String> containerBody = new LinkedMultiValueMap<>();
            containerBody.add("image_url", getAccessibleUrlForInstagramPhoto(post.getImageUrl()));
            containerBody.add("media_type", "IMAGE");
            containerBody.add("caption", post.getCaption() + (post.getHashtags() != null ? "\n\n" + post.getHashtags() : ""));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> containerRequest = new HttpEntity<>(containerBody, headers);

            logger.info("ℹ️ Sending presigned URL to Instagram in request body");

            @SuppressWarnings("unchecked")
            Map<String, Object> containerResponse = executeMetaCallWithRetry(() -> 
                restTemplate.postForObject(containerUrl, containerRequest, Map.class));
                
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

    @SuppressWarnings("unchecked")
    private void waitForMediaStatus(String creationId, String accessToken) {
        boolean isReady = false;
        int retries = 0;
        int maxRetries = 12; // 60 seconds
        
        while (!isReady && retries < maxRetries) {
            try {
                Thread.sleep(5000);
                retries++;
                
                // Fetch status_code AND error details in one call
                String statusUrl = "https://graph.facebook.com/v21.0/" + creationId + 
                                  "?fields=status_code,status&access_token=" + accessToken;
                Map<String, Object> statusResponse = restTemplate.getForObject(statusUrl, Map.class);
                String statusCode = statusResponse != null ? (String) statusResponse.get("status_code") : "UNKNOWN";
                
                logger.info("\ud83d\udcf8 [Polling] Media {} Status: {}", creationId, statusCode);
                
                if ("FINISHED".equalsIgnoreCase(statusCode)) {
                    isReady = true;
                } else if ("ERROR".equalsIgnoreCase(statusCode)) {
                    // Extract detailed error info from Instagram
                    String errorDetail = "";
                    if (statusResponse != null && statusResponse.containsKey("status")) {
                        Object statusObj = statusResponse.get("status");
                        errorDetail = " | Instagram status detail: " + statusObj;
                    }
                    // Also try fetching video_status for more info
                    try {
                        String debugUrl = "https://graph.facebook.com/v21.0/" + creationId + 
                                         "?fields=status_code,status,video_status&access_token=" + accessToken;
                        Map<String, Object> debugResp = restTemplate.getForObject(debugUrl, Map.class);
                        logger.error("❌ [Media ERROR] Full response: {}", debugResp);
                    } catch (Exception ignored) {}
                    throw new RuntimeException("Media processing failed." + errorDetail);
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

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
    private void sendLinkedInPost(Map<String, Object> body, String accessToken, Post post) {
        String url = "https://api.linkedin.com/v2/posts";
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, getLinkedInHeaders(accessToken));
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
        if (response != null && response.containsKey("id")) {
            post.setExternalPostId((String) response.get("id"));
        }
    }

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
    private void publishToInstagramStory(Post post, String igId, String accessToken) {
        try {
            // Step 1: Create Stories Media Container
            // Send image_url in the body to avoid double-encoding
            String containerUrl = UriComponentsBuilder
                    .fromHttpUrl("https://graph.facebook.com/v21.0/" + igId + "/media")
                    .queryParam("access_token", accessToken)
                    .toUriString();

            MultiValueMap<String, String> containerBody = new LinkedMultiValueMap<>();
            containerBody.add("image_url", getAccessibleUrlForInstagramPhoto(post.getImageUrl()));
            containerBody.add("media_type", "STORIES");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(containerBody, headers);

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

            HttpHeaders publishHeaders = new HttpHeaders();
            publishHeaders.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> publishRequest = new HttpEntity<>(publishBody, publishHeaders);

            @SuppressWarnings("unchecked")
            Map<String, Object> publishResponse = restTemplate.postForObject(publishUrl, publishRequest, Map.class);
            post.setExternalPostId((String) publishResponse.get("id"));
            logger.info("📸 Instagram Story Published successfully: {}", post.getExternalPostId());
        } catch (Exception e) {
            logger.error("Instagram Story Error: {}", e.getMessage());
            throw new RuntimeException("Instagram Story publishing failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void publishToFacebookStory(Post post, String pageId, String accessToken) {
        try {
            // Validate that we have media to publish
            if (post.getImageUrl() == null && post.getVideoUrl() == null) {
                throw new RuntimeException("Facebook Story requires either an image or video URL");
            }

            // Step 1: Upload image or video as unpublished
            if (post.getImageUrl() == null && post.getVideoUrl() != null) {
                // TODO: Implement video story publishing
                throw new RuntimeException("Facebook video stories are not yet implemented. Please use an image.");
            }

            // Ensure imageUrl is not null before proceeding
            if (post.getImageUrl() == null) {
                throw new RuntimeException("Image URL is required for Facebook Story");
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

    @SuppressWarnings("unchecked")
    /**
     * Publishes an Instagram Reel using direct video URL approach.
     * 
     * NOTE: Instagram's Reels API does not support resumable upload file handles as video_url.
     * Using file handles causes error 2207072 ("Media upload has failed").
     * This method uses the direct video_url approach which is proven to work reliably.
     * 
     * TESTING: Set app.instagram.reel.skip-transcode=true in application.yml or .env to test with original video
     */
    private void publishReelToInstagram(Post post, String igId, String accessToken) {
        try {
            if (post.getVideoUrl() == null || post.getVideoUrl().isEmpty()) {
                throw new RuntimeException("Video URL is required to publish an Instagram Reel.");
            }

            // Step 1: Get clean public URL
            String cleanUrl = s3Service.resolvePublicVideoUrl(post.getVideoUrl()).trim();

            // Step 2: Check if transcoding should be skipped (for testing)
            if (skipReelTranscode) {
                logger.warn("⚠️ [Instagram Reel] TRANSCODING BYPASSED - Using original video for testing");
                logger.info("🎥 [Instagram Reel] Original video URL: {}", cleanUrl);
            } else if (videoTranscodeService.isFfmpegAvailable()) {
                logger.info("🎬 [Instagram Reel] Transcoding to H.264/AAC...");
                try {
                    cleanUrl = videoTranscodeService.transcodeForInstagram(cleanUrl, post.getUser().getId());
                } catch (Exception e) {
                    logger.warn("⚠️ [Instagram Reel] Transcode failed, using original: {}", e.getMessage());
                }
            }

            // Step 3: Use direct video URL approach (proven to work reliably)
            logger.info("🎥 [Instagram Reel] Publishing via direct video URL approach");
            publishReelToInstagramViaUrl(post, igId, accessToken, cleanUrl);

        } catch (Exception e) {
            logger.error("Instagram Reel Error: {}", e.getMessage());
            throw new RuntimeException("Instagram Reel publishing failed", e);
        }
    }

    // Fallback: video_url approach (used if resumable upload init fails)
    @SuppressWarnings("unchecked")
    private void publishReelToInstagramViaUrl(Post post, String igId, String accessToken, String videoUrl) {
        logger.info("🎥 [Instagram Reel] Using video_url approach: {}", videoUrl);
        
        // Send video_url in the body to avoid double-encoding
        String containerUrl = UriComponentsBuilder
                .fromHttpUrl("https://graph.facebook.com/v21.0/" + igId + "/media")
                .queryParam("access_token", accessToken)
                .toUriString();
        
        // Send all parameters in the body
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("video_url", videoUrl);
        body.add("media_type", "REELS");
        body.add("caption", post.getCaption() + (post.getHashtags() != null ? "\n\n" + post.getHashtags() : ""));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        @SuppressWarnings("unchecked")
        Map<String, Object> containerResponse = restTemplate.postForObject(containerUrl, request, Map.class);
        String creationId = (String) containerResponse.get("id");
        if (creationId == null) throw new RuntimeException("Failed to create IG Reels container via URL.");

        waitForMediaStatus(creationId, accessToken);

        String publishUrl = "https://graph.facebook.com/v21.0/" + igId + "/media_publish";
        Map<String, Object> publishBody = new HashMap<>();
        publishBody.put("creation_id", creationId);
        publishBody.put("access_token", accessToken);

        HttpHeaders publishHeaders = new HttpHeaders();
        publishHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> publishRequest = new HttpEntity<>(publishBody, publishHeaders);

        @SuppressWarnings("unchecked")
        Map<String, Object> publishResponse = restTemplate.postForObject(publishUrl, publishRequest, Map.class);
        post.setExternalPostId((String) publishResponse.get("id"));
        logger.info("🎥 Instagram Reel Published (via URL): {}", post.getExternalPostId());
    }

    @SuppressWarnings("unchecked")
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

            // Facebook requires a clean, publicly accessible URL without pre-signed query params.
            // Pre-signed S3 URLs (with X-Amz-Signature etc.) are rejected by Facebook's crawler (error 389).
            // resolvePublicVideoUrl strips query params AND makes the S3 object public-read if needed.
            String fallbackUrl = "https://graph.facebook.com/v21.0/" + pageId + "/videos";
            MultiValueMap<String, Object> videoBody = new LinkedMultiValueMap<>();

            String cleanUrl = s3Service.resolvePublicVideoUrl(post.getVideoUrl());
            logger.info("\ud83c\udfac Sending Reel/Video URL to Facebook: [{}]", cleanUrl);

            videoBody.add("file_url", cleanUrl);
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

    @SuppressWarnings("unchecked")
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
    private String getAccessibleUrl(String mediaUrl) {
        if (mediaUrl == null) return null;
        
        // If it's an S3 URL from our bucket, generate a presigned URL
        if (mediaUrl.contains(".amazonaws.com/")) {
            try {
                String key = s3Service.extractKeyFromUrl(mediaUrl);
                // Presigned URL for 1 hour
                String presignedUrl = s3Service.generatePresignedReadUrl(key);
                
                // Return the presigned URL as-is without any string manipulation
                // String replacement on an already-encoded URL causes double-encoding issues
                // which Instagram's API rejects (e.g., %252F instead of %2F)
                return presignedUrl;
            } catch (Exception e) {
                logger.warn("⚠️ Failed to presign URL: {}. Falling back to original.", e.getMessage());
            }
        }
        
        return mediaUrl;
    }

    /**
     * Resolves an S3 URL to a clean public URL for Instagram photos.
     * 
     * NOTE: Instagram's API rejects presigned S3 URLs with authentication parameters (error 2207052).
     * This method makes the S3 object publicly readable and returns a clean permanent URL.
     * 
     * @param mediaUrl The S3 URL to resolve
     * @return Clean public URL without query parameters
     */
    private String getAccessibleUrlForInstagramPhoto(String mediaUrl) {
        if (mediaUrl == null) return null;
        
        // If it's an S3 URL, make it public and return clean URL (same as Instagram Reels)
        if (mediaUrl.contains(".amazonaws.com/")) {
            try {
                logger.info("📸 [Instagram Photo] Making S3 object public and returning clean URL");
                return s3Service.resolvePublicVideoUrl(mediaUrl);
            } catch (Exception e) {
                logger.warn("⚠️ Failed to make Instagram photo public: {}. Falling back to presigned URL.", e.getMessage());
                // Fallback to presigned URL if making public fails
                return getAccessibleUrl(mediaUrl);
            }
        }
        
        return mediaUrl;
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
