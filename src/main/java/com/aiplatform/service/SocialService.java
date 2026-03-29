package com.aiplatform.service;

import com.aiplatform.model.SocialAccount;
import com.aiplatform.model.User;
import com.aiplatform.repository.SocialAccountRepository;
import com.aiplatform.security.EncryptionUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class SocialService {

    private static final Logger logger = LoggerFactory.getLogger(SocialService.class);

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private EncryptionUtils encryptionUtils;

    @Value("${fb.app.id}")
    private String fbAppId;

    @Value("${fb.app.secret}")
    private String fbAppSecret;

    @Value("${fb.redirect.uri}")
    private String fbRedirectUri;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String getFacebookAuthUrl(String state) {
        return "https://www.facebook.com/v19.0/dialog/oauth?" +
                "client_id=" + fbAppId +
                "&redirect_uri=" + fbRedirectUri +
                "&state=" + state +
                "&scope=pages_show_list,pages_manage_posts,instagram_basic,instagram_content_publish,pages_read_engagement";
    }

    public void processFacebookCallback(String code, Long userId, User user) {
        // ── Step 1: Exchange authorization code for a user access token ─────────
        String tokenUrl = "https://graph.facebook.com/v19.0/oauth/access_token?" +
                "client_id=" + fbAppId +
                "&redirect_uri=" + fbRedirectUri +
                "&client_secret=" + fbAppSecret +
                "&code=" + code;

        Map<String, Object> tokenResponse = restTemplate.getForObject(tokenUrl, Map.class);
        if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
            throw new RuntimeException("Failed to obtain user access token from Facebook.");
        }
        String userAccessToken = (String) tokenResponse.get("access_token");
        Integer expiresIn = (Integer) tokenResponse.getOrDefault("expires_in", 5184000); // default 60 days

        logger.info("Successfully obtained Facebook user access token for userId={}", userId);

        // ── Step 2: Fetch Facebook Pages linked to this user ────────────────────
        String pagesUrl = "https://graph.facebook.com/v19.0/me/accounts?access_token=" + userAccessToken;
        ResponseEntity<String> pagesResponseEntity = restTemplate.getForEntity(pagesUrl, String.class);

        JsonNode pagesRoot;
        try {
            pagesRoot = objectMapper.readTree(pagesResponseEntity.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Facebook pages response", e);
        }

        JsonNode pagesData = pagesRoot.path("data");
        if (pagesData.isEmpty()) {
            throw new RuntimeException("No Facebook Pages found for this user. " +
                    "Make sure the user has a Facebook Page and granted pages_show_list permission.");
        }

        // ── Step 3: For each page, save a FACEBOOK account and look up IG ───────
        for (JsonNode page : pagesData) {
            String pageId = page.path("id").asText();
            String pageName = page.path("name").asText();
            String pageAccessToken = page.path("access_token").asText();

            if (pageId.isEmpty() || pageAccessToken.isEmpty()) {
                logger.warn("Skipping page with missing id or access_token: {}", page);
                continue;
            }

            logger.info("Processing Facebook Page '{}' (id={})", pageName, pageId);

            // Encrypt the page access token (NOT the user token — the page token is
            // what Graph API requires for feed/photo publishing).
            String encryptedPageToken;
            try {
                encryptedPageToken = encryptionUtils.encrypt(pageAccessToken);
            } catch (Exception e) {
                throw new RuntimeException("Error encrypting page access token", e);
            }

            // Delete any existing Facebook account for this user+page to avoid duplicates
            socialAccountRepository.findByUserAndPlatformAndPageId(user, "FACEBOOK", pageId)
                    .ifPresent(existing -> socialAccountRepository.delete(existing));

            // Fetch the Facebook page's profile picture
            String pagePictureUrl = null;
            try {
                String picUrl = "https://graph.facebook.com/v19.0/" + pageId +
                        "/picture?type=large&redirect=false&access_token=" + pageAccessToken;
                ResponseEntity<String> picResponse = restTemplate.getForEntity(picUrl, String.class);
                JsonNode picRoot = objectMapper.readTree(picResponse.getBody());
                pagePictureUrl = picRoot.path("data").path("url").asText(null);
            } catch (Exception e) {
                logger.warn("Could not fetch profile picture for pageId={}: {}", pageId, e.getMessage());
            }

            SocialAccount fbAccount = SocialAccount.builder()
                    .user(user)
                    .platform("FACEBOOK")
                    .pageId(pageId)
                    .accountName(pageName)
                    .profilePictureUrl(pagePictureUrl)
                    .encryptedAccessToken(encryptedPageToken)
                    .tokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn))
                    .build();

            socialAccountRepository.save(fbAccount);
            logger.info("Saved FACEBOOK account for pageId={}", pageId);

            // ── Step 4: Fetch Instagram Business Account linked to this page ────
            String igUrl = "https://graph.facebook.com/v19.0/" + pageId +
                    "?fields=instagram_business_account&access_token=" + pageAccessToken;

            try {
                ResponseEntity<String> igResponseEntity = restTemplate.getForEntity(igUrl, String.class);
                JsonNode igRoot = objectMapper.readTree(igResponseEntity.getBody());
                JsonNode igNode = igRoot.path("instagram_business_account");

                if (!igNode.isMissingNode() && !igNode.path("id").asText().isEmpty()) {
                    String igBusinessId = igNode.path("id").asText();
                    logger.info("Found Instagram Business Account id={} for pageId={}", igBusinessId, pageId);

                    // Encrypt the same page access token for Instagram
                    String encryptedIgToken;
                    try {
                        encryptedIgToken = encryptionUtils.encrypt(pageAccessToken);
                    } catch (Exception e) {
                        throw new RuntimeException("Error encrypting IG access token", e);
                    }

                    // Delete any existing IG account to avoid duplicates
                    socialAccountRepository.findByUserAndPlatformAndIgBusinessAccountId(user, "INSTAGRAM", igBusinessId)
                            .ifPresent(existing -> socialAccountRepository.delete(existing));

                    // Fetch IG username and profile picture
                    String igUsername = null;
                    String igPictureUrl = null;
                    try {
                        String igProfileUrl = "https://graph.facebook.com/v19.0/" + igBusinessId +
                                "?fields=username,profile_picture_url&access_token=" + pageAccessToken;
                        ResponseEntity<String> igProfileResponse = restTemplate.getForEntity(igProfileUrl, String.class);
                        JsonNode igProfileRoot = objectMapper.readTree(igProfileResponse.getBody());
                        igUsername = igProfileRoot.path("username").asText(null);
                        igPictureUrl = igProfileRoot.path("profile_picture_url").asText(null);
                        logger.info("Fetched IG profile: username={}", igUsername);
                    } catch (Exception e) {
                        logger.warn("Could not fetch IG profile for igBusinessId={}: {}", igBusinessId, e.getMessage());
                    }

                    SocialAccount igAccount = SocialAccount.builder()
                            .user(user)
                            .platform("INSTAGRAM")
                            .igBusinessAccountId(igBusinessId)
                            .accountName(igUsername != null ? "@" + igUsername : "IG Business Account")
                            .profilePictureUrl(igPictureUrl)
                            .encryptedAccessToken(encryptedIgToken)
                            .tokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn))
                            .build();

                    socialAccountRepository.save(igAccount);
                    logger.info("Saved INSTAGRAM account for igBusinessId={}", igBusinessId);
                } else {
                    logger.warn("No Instagram Business Account linked to Facebook Page id={}. " +
                            "Make sure the IG account is a Business/Creator account connected to the page.", pageId);
                }
            } catch (Exception e) {
                // Log but do not fail — Facebook account was already saved successfully.
                logger.warn("Could not fetch Instagram Business Account for pageId={}: {}", pageId, e.getMessage());
            }
        }
    }

    public List<SocialAccount> getAccountsByUser(User user) {
        return socialAccountRepository.findByUser(user);
    }

    public void deleteAccount(Long id, User user) {
        SocialAccount account = socialAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Social account not found"));
        
        if (!account.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You are not authorized to disconnect this account");
        }
        
        socialAccountRepository.delete(account);
    }
}
