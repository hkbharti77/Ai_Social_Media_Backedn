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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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

    @Value("${fb.app.access-token}")
    private String defaultAccessToken;

    @Value("${fb.redirect.uri}")
    private String fbRedirectUri;

    @Value("${linkedin.client.id}")
    private String liClientId;

    @Value("${linkedin.client.secret}")
    private String liClientSecret;

    @Value("${linkedin.redirect.uri}")
    private String liRedirectUri;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String getFacebookAuthUrl(String state) {
        return "https://www.facebook.com/v19.0/dialog/oauth?" +
                "client_id=" + fbAppId +
                "&redirect_uri=" + fbRedirectUri +
                "&state=" + state +
                "&scope=pages_show_list,pages_manage_posts,instagram_basic,instagram_content_publish,pages_read_engagement";
    }

    public String getLinkedInAuthUrl(String state) {
        return "https://www.linkedin.com/oauth/v2/authorization?" +
                "response_type=code" +
                "&client_id=" + liClientId +
                "&redirect_uri=" + liRedirectUri +
                "&state=" + state +
                "&scope=openid%20profile%20w_member_social%20email";
    }

    public void processFacebookCallback(String code, Long userId, User user) {
        // ── Step 1: Exchange authorization code for a user access token ─────────
        String tokenUrl = "https://graph.facebook.com/v19.0/oauth/access_token?" +
                "client_id=" + fbAppId +
                "&redirect_uri=" + fbRedirectUri +
                "&client_secret=" + fbAppSecret +
                "&code=" + code;

        @SuppressWarnings("unchecked")
        Map<String, Object> tokenResponse = restTemplate.getForObject(tokenUrl, Map.class);
        if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
            throw new RuntimeException("Failed to obtain user access token from Facebook.");
        }
        String userAccessToken = (String) tokenResponse.get("access_token");
        Integer expiresIn = (Integer) tokenResponse.getOrDefault("expires_in", 5184000); // default 60 days

        logger.info("Successfully obtained Facebook user access token for userId={}", userId);

        fetchAndSaveAccounts(userAccessToken, expiresIn, user);
    }

    public void processLinkedInCallback(String code, User user) {
        // Exchange code for access token
        String tokenUrl = "https://www.linkedin.com/oauth/v2/accessToken";
        
        MultiValueMap<String, String> body = new org.springframework.util.LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("client_id", liClientId);
        body.add("client_secret", liClientSecret);
        body.add("redirect_uri", liRedirectUri);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> tokenResponse = restTemplate.postForObject(tokenUrl, request, Map.class);
        
        if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
            throw new RuntimeException("Failed to obtain LinkedIn access token.");
        }
        
        String accessToken = (String) tokenResponse.get("access_token");
        Integer expiresIn = (Integer) tokenResponse.getOrDefault("expires_in", 5184000);

        // Fetch user profile info using OpenID Connect endpoint
        try {
            HttpHeaders infoHeaders = new HttpHeaders();
            infoHeaders.setBearerAuth(accessToken);
            HttpEntity<String> infoRequest = new HttpEntity<>(infoHeaders);
            
            ResponseEntity<String> infoResponse = restTemplate.exchange(
                "https://api.linkedin.com/v2/userinfo",
                org.springframework.http.HttpMethod.GET,
                infoRequest,
                String.class
            );
            
            JsonNode profileRoot = objectMapper.readTree(infoResponse.getBody());
            String urnSub = profileRoot.path("sub").asText();
            String name = profileRoot.path("name").asText();
            String picture = profileRoot.path("picture").asText(null);
            
            if (urnSub.isEmpty()) {
                throw new RuntimeException("Failed to retrieve LinkedIn user ID (sub).");
            }
            
            String personUrn = "urn:li:person:" + urnSub;
            
            // Encrypt token
            String encryptedToken = encryptionUtils.encrypt(accessToken);
            
            // Delete existing LinkedIn account for this user to avoid duplicates
            socialAccountRepository.findByUserAndPlatformAndPageId(user, "LINKEDIN", personUrn)
                .ifPresent(existing -> socialAccountRepository.delete(existing));
                
            SocialAccount liAccount = SocialAccount.builder()
                .user(user)
                .platform("LINKEDIN")
                .pageId(personUrn)
                .accountName(name)
                .profilePictureUrl(picture)
                .encryptedAccessToken(encryptedToken)
                .tokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn))
                .build();
                
            socialAccountRepository.save(liAccount);
            logger.info("Saved LINKEDIN account for personUrn={}", personUrn);
            
        } catch (Exception e) {
            throw new RuntimeException("Error processing LinkedIn profile: " + e.getMessage(), e);
        }
    }

    public void connectWithUserToken(String userToken, User user) {
        // First, exchange the potentially short-lived token for a long-lived one
        Map<String, Object> longLivedTokenResponse = exchangeShortLivedToken(userToken);
        String longLivedToken = (String) longLivedTokenResponse.get("access_token");
        Integer expiresIn = (Integer) longLivedTokenResponse.getOrDefault("expires_in", 5184000);

        fetchAndSaveAccounts(longLivedToken, expiresIn, user);
    }

    private Map<String, Object> exchangeShortLivedToken(String shortLivedToken) {
        String exchangeUrl = "https://graph.facebook.com/v19.0/oauth/access_token?" +
                "grant_type=fb_exchange_token" +
                "&client_id=" + fbAppId +
                "&client_secret=" + fbAppSecret +
                "&fb_exchange_token=" + shortLivedToken;

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.getForObject(exchangeUrl, Map.class);
        if (response == null || !response.containsKey("access_token")) {
            throw new RuntimeException("Failed to exchange short-lived token for long-lived token.");
        }
        return response;
    }

    private void fetchAndSaveAccounts(String userAccessToken, Integer expiresIn, User user) {
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

            // Encrypt the page access token
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
                    logger.info("\ud83d\udcf7 [Instagram] Linked IG Business Account found: {}", igBusinessId);

                    // Encrypt the same page access token for Instagram
                    String encryptedIgToken = encryptionUtils.encrypt(pageAccessToken);

                    // Delete any existing IG account to avoid duplicates
                    socialAccountRepository.findByUserAndPlatformAndIgBusinessAccountId(user, "INSTAGRAM", igBusinessId)
                            .ifPresent(existing -> {
                                logger.info("\ud83d\udd04 [Instagram] Updating existing IG account entry for id={}", igBusinessId);
                                socialAccountRepository.delete(existing);
                            });

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
                    } catch (Exception e) {
                        logger.warn("\u26a0\ufe0f [Instagram] Optional profile data fetch failed: {}", e.getMessage());
                    }

                    SocialAccount igAccount = SocialAccount.builder()
                            .user(user)
                            .platform("INSTAGRAM")
                            .igBusinessAccountId(igBusinessId)
                            .accountName(igUsername != null ? "@" + igUsername : "Instagram Business")
                            .profilePictureUrl(igPictureUrl)
                            .encryptedAccessToken(encryptedIgToken)
                            .tokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn))
                            .build();

                    socialAccountRepository.save(igAccount);
                    logger.info("\u2705 [Instagram] Account successfully integrated.");
                } else {
                    logger.warn("\ud83d\udeab [Instagram] No Instagram Business Account linked to FB Page {}. " +
                            "Verify that your Instagram account is a 'Business' or 'Creator' type and is connected to this Facebook Page in Page Settings.", pageId);
                }
            } catch (Exception e) {
                logger.warn("\u274c [Instagram] API connection failed for pageId={}: {}", pageId, e.getMessage());
            }
        }
    }


    public void connectWithConfiguredToken(User user) {
        if (defaultAccessToken == null || defaultAccessToken.isEmpty()) {
            throw new RuntimeException("No default access token configured in application.yml");
        }
        connectWithUserToken(defaultAccessToken, user);
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
