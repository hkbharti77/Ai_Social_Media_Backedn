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
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
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
                if (post.getPlatform().equalsIgnoreCase("BOTH") || 
                    post.getPlatform().equalsIgnoreCase(account.getPlatform())) {
                    
                    String token = encryptionUtils.decrypt(account.getEncryptedAccessToken());
                    
                    if (account.getPlatform().equalsIgnoreCase("FACEBOOK")) {
                        publishToFacebook(post, account.getPageId(), token);
                        published = true;
                    } else if (account.getPlatform().equalsIgnoreCase("INSTAGRAM")) {
                        publishToInstagram(post, account.getIgBusinessAccountId(), token);
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

        Map<String, Object> containerResponse = restTemplate.postForObject(containerUrl, containerBody, Map.class);
        String creationId = (String) containerResponse.get("id");

        // Step 2: Publish media
        String publishUrl = "https://graph.facebook.com/v19.0/" + igId + "/media_publish";
        MultiValueMap<String, Object> publishBody = new LinkedMultiValueMap<>();
        publishBody.add("creation_id", creationId);
        publishBody.add("access_token", accessToken);

        Map<String, Object> publishResponse = restTemplate.postForObject(publishUrl, publishBody, Map.class);
        
        if (publishResponse != null && publishResponse.containsKey("id")) {
            post.setExternalPostId((String) publishResponse.get("id"));
        } else {
            throw new RuntimeException("Failed to publish to Instagram. Response: " + publishResponse);
        }
    }
}
