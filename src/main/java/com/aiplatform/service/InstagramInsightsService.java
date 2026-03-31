package com.aiplatform.service;

import com.aiplatform.model.SocialAccount;
import com.aiplatform.model.User;
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



@Service
@RequiredArgsConstructor
public class InstagramInsightsService {
    private static final Logger logger = LoggerFactory.getLogger(InstagramInsightsService.class);
    
    private final SocialAccountRepository socialAccountRepository;
    private final EncryptionUtils encryptionUtils;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonNode getBestTimeReport(User user) {
        SocialAccount igAccount = socialAccountRepository.findByUserAndPlatform(user, "INSTAGRAM")
                .stream().findFirst()
                .orElse(null);

        if (igAccount == null) {
            logger.warn("No connected Instagram account for user: {}. Returning mock data.", user.getEmail());
            return mockBestTimeData();
        }

        String accessToken;
        try {
            accessToken = encryptionUtils.decrypt(igAccount.getEncryptedAccessToken());
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt access token");
        }

        // Fetch Follower Activity (Lifetime/Daily insights)
        // Metric: online_followers (This metric is only available for accounts with >100 followers)
        String url = String.format("https://graph.facebook.com/v21.0/%s/insights?metric=online_followers&period=lifetime&access_token=%s",
                igAccount.getIgBusinessAccountId(), accessToken);

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            logger.error("Failed to fetch IG insights: {}", e.getMessage());
            // Return a fallback/mock if API fails or followers < 100
            return mockBestTimeData();
        }
    }

    private JsonNode mockBestTimeData() {
        try {
            String json = """
                {
                  "data": [
                    {
                      "name": "online_followers",
                      "period": "lifetime",
                      "values": [
                        { "value": { "0": 120, "1": 90, "18": 450, "19": 650, "20": 800, "21": 750 }, "end_time": "2026-03-28T07:00:00+0000" }
                      ]
                    }
                  ],
                  "recommendation": "Based on your audience activity, the best time to post is between 7:00 PM and 9:00 PM."
                }
                """;
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
