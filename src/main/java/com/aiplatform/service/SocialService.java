package com.aiplatform.service;

import com.aiplatform.model.SocialAccount;
import com.aiplatform.model.User;
import com.aiplatform.repository.SocialAccountRepository;
import com.aiplatform.repository.UserRepository;
import com.aiplatform.security.EncryptionUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

@Service
public class SocialService {

    private static final Logger logger = LoggerFactory.getLogger(SocialService.class);

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private EncryptionUtils encryptionUtils;

    @Autowired
    private EmailService emailService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

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

    @Value("${x.client.id}")
    private String xClientId;

    @Value("${x.client.secret}")
    private String xClientSecret;

    @Value("${x.redirect.uri}")
    private String xRedirectUri;

    @Value("${x.default.access-token:}")
    private String xDefaultAccessToken;

    @Value("${x.default.refresh-token:}")
    private String xDefaultRefreshToken;

    @Value("${x.default.user-id:}")
    private String xDefaultUserId;

    @Value("${app.security.owner-email}")
    private String ownerEmail;

    @Autowired
    private RedisClient redisClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * On application startup, if X_DEFAULT_ACCESS_TOKEN is set in .env,
     * automatically save it as the owner's X account — no API call needed.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void autoConnectXFromEnv() {
        if (xDefaultAccessToken == null || xDefaultAccessToken.isBlank()) {
            logger.info("[X] No X_DEFAULT_ACCESS_TOKEN configured — skipping auto-connect.");
            return;
        }

        try {
            // Find the owner user by email
            User owner = userRepository.findByEmail(ownerEmail).orElse(null);
            if (owner == null) {
                logger.warn("[X] Owner account ({}) not found in DB — skipping X auto-connect.", ownerEmail);
                return;
            }

            // Skip if already connected with this user ID
            if (xDefaultUserId != null && !xDefaultUserId.isBlank()) {
                boolean alreadyConnected = socialAccountRepository
                        .findByUserAndPlatformAndPageId(owner, "X", xDefaultUserId)
                        .isPresent();
                if (alreadyConnected) {
                    logger.info("[X] X account (userId={}) already connected for owner — skipping.", xDefaultUserId);
                    return;
                }
            }

            // Verify token and save account
            connectXManually(xDefaultAccessToken, xDefaultRefreshToken, owner);
            logger.info("✅ [X] Auto-connected X account from .env for owner: {}", ownerEmail);

        } catch (Exception e) {
            logger.error("❌ [X] Auto-connect from .env failed: {}. " +
                    "Check X_DEFAULT_ACCESS_TOKEN in your .env file.", e.getMessage());
        }
    }

    public String getFacebookAuthUrl(String state) {
        return "https://www.facebook.com/v19.0/dialog/oauth?" +
                "client_id=" + fbAppId +
                "&redirect_uri=" + fbRedirectUri +
                "&state=" + state +
                "&scope=pages_show_list,pages_manage_posts,instagram_basic,instagram_content_publish,pages_read_engagement,instagram_manage_insights,instagram_manage_comments";
    }

    public String getLinkedInAuthUrl(String state) {
        return "https://www.linkedin.com/oauth/v2/authorization?" +
                "response_type=code" +
                "&client_id=" + liClientId +
                "&redirect_uri=" + liRedirectUri +
                "&state=" + state +
                "&scope=openid%20profile%20w_member_social%20email";
    }

    public String getXAuthUrl(String state) {
        // PKCE: 1. Generate code_verifier
        String codeVerifier = generateCodeVerifier();
        
        // 2. Generate code_challenge
        String codeChallenge = generateCodeChallenge(codeVerifier);
        
        // 3. Store code_verifier in Redis linked to state (valid for 10 mins)
        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            RedisCommands<String, String> sync = connection.sync();
            sync.setex("pkce:" + state, 600, codeVerifier);
        }

        return "https://twitter.com/i/oauth2/authorize?" +
                "response_type=code" +
                "&client_id=" + xClientId +
                "&redirect_uri=" + xRedirectUri +
                "&state=" + state +
                "&code_challenge=" + codeChallenge +
                "&code_challenge_method=S256" +
                "&scope=tweet.read%20tweet.write%20users.read%20offline.access";
    }

    private String generateCodeVerifier() {
        SecureRandom sr = new SecureRandom();
        byte[] code = new byte[32];
        sr.nextBytes(code);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(code);
    }

    private String generateCodeChallenge(String codeVerifier) {
        try {
            byte[] bytes = codeVerifier.getBytes();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(bytes);
            byte[] digest = md.digest();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException("Error generating code challenge", e);
        }
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
            emailService.sendSocialAccountConnectedEmail(user, "LINKEDIN", name);
            
        } catch (Exception e) {
            throw new RuntimeException("Error processing LinkedIn profile: " + e.getMessage(), e);
        }
    }

    public void processXCallback(String code, String state, User user) {
        // Retrieve code_verifier from Redis
        String codeVerifier;
        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            RedisCommands<String, String> sync = connection.sync();
            codeVerifier = sync.get("pkce:" + state);
        }

        if (codeVerifier == null) {
            throw new RuntimeException("Invalid state or PKCE verifier expired.");
        }

        // Exchange code for tokens
        String tokenUrl = "https://api.twitter.com/2/oauth2/token";

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", xRedirectUri);
        body.add("code_verifier", codeVerifier);
        body.add("client_id", xClientId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(xClientId, xClientSecret);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> tokenResponse = restTemplate.postForObject(tokenUrl, request, Map.class);
            if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
                throw new RuntimeException("Failed to obtain X access token.");
            }

            String accessToken = (String) tokenResponse.get("access_token");
            String refreshToken = (String) tokenResponse.get("refresh_token");
            Integer expiresIn = (Integer) tokenResponse.getOrDefault("expires_in", 7200);

            // Fetch user info
            HttpHeaders userInfoHeaders = new HttpHeaders();
            userInfoHeaders.setBearerAuth(accessToken);
            HttpEntity<String> userInfoRequest = new HttpEntity<>(userInfoHeaders);

            ResponseEntity<String> userInfoResponse = restTemplate.exchange(
                    "https://api.twitter.com/2/users/me?user.fields=profile_image_url",
                    HttpMethod.GET,
                    userInfoRequest,
                    String.class
            );

            JsonNode userRoot = objectMapper.readTree(userInfoResponse.getBody());
            JsonNode dataNode = userRoot.path("data");
            String xUserId = dataNode.path("id").asText();
            String xUsername = dataNode.path("username").asText();
            String xName = dataNode.path("name").asText();
            String xProfilePicture = dataNode.path("profile_image_url").asText(null);

            // Encrypt and save
            String encryptedAccessToken = encryptionUtils.encrypt(accessToken);
            String encryptedRefreshToken = refreshToken != null ? encryptionUtils.encrypt(refreshToken) : null;

            socialAccountRepository.findByUserAndPlatformAndPageId(user, "X", xUserId)
                    .ifPresent(existing -> socialAccountRepository.delete(existing));

            SocialAccount xAccount = SocialAccount.builder()
                    .user(user)
                    .platform("X")
                    .pageId(xUserId)
                    .accountName(xName + " (@" + xUsername + ")")
                    .profilePictureUrl(xProfilePicture)
                    .encryptedAccessToken(encryptedAccessToken)
                    .encryptedRefreshToken(encryptedRefreshToken)
                    .tokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn))
                    .build();

            socialAccountRepository.save(xAccount);
            logger.info("Saved X account for userId={}", xUserId);
            emailService.sendSocialAccountConnectedEmail(user, "X", xName + " (@" + xUsername + ")");

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String errorDetail = e.getResponseBodyAsString();
            logger.error("❌ X Token Exchange Failed: {} - {}", e.getStatusCode(), errorDetail);
            try {
                JsonNode errorNode = objectMapper.readTree(errorDetail);
                String detail = errorNode.path("detail").asText(errorNode.path("error_description").asText("X API Error: " + e.getStatusText()));
                throw new RuntimeException(detail);
            } catch (Exception ex) {
                throw new RuntimeException("X API Error: " + e.getStatusText() + " (" + errorDetail + ")");
            }
        } catch (Exception e) {
            logger.error("X Callback error", e);
            throw new RuntimeException("Error processing X callback: " + e.getMessage());
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
        // ── Step 1.5: Fetch Facebook User ID ─────────────────────────────────────
        String meUrl = "https://graph.facebook.com/v19.0/me?access_token=" + userAccessToken;
        String facebookUserId = null;
        try {
            ResponseEntity<String> meResponse = restTemplate.getForEntity(meUrl, String.class);
            JsonNode meRoot = objectMapper.readTree(meResponse.getBody());
            facebookUserId = meRoot.path("id").asText(null);
        } catch (Exception e) {
            logger.warn("Could not fetch Facebook User ID: {}", e.getMessage());
        }

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
                    .facebookUserId(facebookUserId)
                    .pageId(pageId)
                    .accountName(pageName)
                    .profilePictureUrl(pagePictureUrl)
                    .encryptedAccessToken(encryptedPageToken)
                    .tokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn))
                    .build();

            socialAccountRepository.save(fbAccount);
            logger.info("Saved FACEBOOK account for pageId={}", pageId);
            emailService.sendSocialAccountConnectedEmail(user, "FACEBOOK", pageName);

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
                    emailService.sendSocialAccountConnectedEmail(user, "INSTAGRAM",
                        igUsername != null ? "@" + igUsername : "Instagram Business");
                } else {
                    logger.warn("\ud83d\udeab [Instagram] No Instagram Business Account linked to FB Page {}. " +
                            "Verify that your Instagram account is a 'Business' or 'Creator' type and is connected to this Facebook Page in Page Settings.", pageId);
                }
            } catch (Exception e) {
                logger.warn("\u274c [Instagram] API connection failed for pageId={}: {}", pageId, e.getMessage());
            }
        }
    }


    /**
     * Manually connect an X account using tokens generated from the X Developer Portal.
     * This is used when the standard OAuth callback flow is not available (e.g., dev/testing).
     */
    public void connectXManually(String accessToken, String refreshToken, User user) {
        try {
            // Verify the token by fetching user info
            HttpHeaders userInfoHeaders = new HttpHeaders();
            userInfoHeaders.setBearerAuth(accessToken);
            HttpEntity<String> userInfoRequest = new HttpEntity<>(userInfoHeaders);

            ResponseEntity<String> userInfoResponse = restTemplate.exchange(
                    "https://api.twitter.com/2/users/me?user.fields=profile_image_url",
                    HttpMethod.GET,
                    userInfoRequest,
                    String.class
            );

            JsonNode userRoot = objectMapper.readTree(userInfoResponse.getBody());
            JsonNode dataNode = userRoot.path("data");
            String xUserId = dataNode.path("id").asText();
            String xUsername = dataNode.path("username").asText();
            String xName = dataNode.path("name").asText();
            String xProfilePicture = dataNode.path("profile_image_url").asText(null);

            if (xUserId.isEmpty()) {
                throw new RuntimeException("Could not retrieve X user ID. Token may be invalid.");
            }

            // Encrypt and save
            String encryptedAccessToken = encryptionUtils.encrypt(accessToken);
            String encryptedRefreshToken = refreshToken != null && !refreshToken.isBlank()
                    ? encryptionUtils.encrypt(refreshToken) : null;

            // Remove any existing X account for this user to avoid duplicates
            socialAccountRepository.findByUserAndPlatformAndPageId(user, "X", xUserId)
                    .ifPresent(existing -> socialAccountRepository.delete(existing));

            SocialAccount xAccount = SocialAccount.builder()
                    .user(user)
                    .platform("X")
                    .pageId(xUserId)
                    .accountName(xName + " (@" + xUsername + ")")
                    .profilePictureUrl(xProfilePicture)
                    .encryptedAccessToken(encryptedAccessToken)
                    .encryptedRefreshToken(encryptedRefreshToken)
                    // X OAuth 2.0 user tokens expire in 2 hours; refresh token is long-lived
                    .tokenExpiresAt(LocalDateTime.now().plusHours(2))
                    .build();

            socialAccountRepository.save(xAccount);
            logger.info("✅ Manually connected X account @{} for userId={}", xUsername, user.getId());
            emailService.sendSocialAccountConnectedEmail(user, "X", xName + " (@" + xUsername + ")");

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String errorBody = e.getResponseBodyAsString();
            logger.error("❌ X token verification failed: {} - {}", e.getStatusCode(), errorBody);
            throw new RuntimeException("X token verification failed: " + e.getStatusText() + ". Make sure the access token is valid and has tweet.read + users.read scopes.");
        } catch (Exception e) {
            logger.error("❌ Error connecting X account manually: {}", e.getMessage());
            throw new RuntimeException("Error connecting X account: " + e.getMessage(), e);
        }
    }

    /**
     * Refresh an X access token using the stored refresh token.
     * X OAuth 2.0 access tokens expire in 2 hours; this exchanges the refresh token for a new one.
     */
    public String refreshXAccessToken(SocialAccount account) {
        String encryptedRefreshToken = account.getEncryptedRefreshToken();
        if (encryptedRefreshToken == null || encryptedRefreshToken.isBlank()) {
            throw new RuntimeException("No refresh token stored for X account: " + account.getAccountName());
        }

        String refreshToken;
        try {
            refreshToken = encryptionUtils.decrypt(encryptedRefreshToken);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt X refresh token: " + e.getMessage(), e);
        }

        String tokenUrl = "https://api.twitter.com/2/oauth2/token";

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", refreshToken);
        body.add("client_id", xClientId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(xClientId, xClientSecret);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> tokenResponse = restTemplate.postForObject(tokenUrl, request, Map.class);
            if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
                throw new RuntimeException("X token refresh returned empty response.");
            }

            String newAccessToken = (String) tokenResponse.get("access_token");
            String newRefreshToken = (String) tokenResponse.get("refresh_token");
            Integer expiresIn = (Integer) tokenResponse.getOrDefault("expires_in", 7200);

            // Persist the new tokens
            account.setEncryptedAccessToken(encryptionUtils.encrypt(newAccessToken));
            if (newRefreshToken != null) {
                account.setEncryptedRefreshToken(encryptionUtils.encrypt(newRefreshToken));
            }
            account.setTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
            socialAccountRepository.save(account);

            logger.info("✅ [X] Access token refreshed for account: {}", account.getAccountName());
            return newAccessToken;

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String errorBody = e.getResponseBodyAsString();
            logger.error("❌ [X] Token refresh HTTP error: {} - {}", e.getStatusCode(), errorBody);
            throw new RuntimeException("X token refresh failed: " + e.getStatusText() + " — " + errorBody);
        } catch (Exception e) {
            logger.error("❌ [X] Token refresh error: {}", e.getMessage());
            throw new RuntimeException("X token refresh failed: " + e.getMessage(), e);
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

        String platform = account.getPlatform();
        String accountName = account.getAccountName();
        socialAccountRepository.delete(account);
        emailService.sendSocialAccountDisconnectedEmail(user, platform, accountName);
    }
}
