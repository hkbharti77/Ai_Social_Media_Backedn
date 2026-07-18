package com.aiplatform.service;

import com.aiplatform.model.DataDeletionStatus;
import com.aiplatform.model.SocialAccount;
import com.aiplatform.repository.DataDeletionStatusRepository;
import com.aiplatform.repository.SocialAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class FacebookDataDeletionService {
    private static final Logger logger = LoggerFactory.getLogger(FacebookDataDeletionService.class);

    @Value("${fb.app.secret}")
    private String fbAppSecret;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private DataDeletionStatusRepository deletionStatusRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String processDataDeletion(String signedRequest) {
        try {
            String[] split = signedRequest.split("\\.", 2);
            if (split.length != 2) {
                throw new IllegalArgumentException("Invalid signed request format");
            }
            String encodedSig = split[0];
            String payload = split[1];

            // Verify signature
            byte[] expectedSig = hmacSha256(payload, fbAppSecret);
            byte[] decodedSig = base64UrlDecode(encodedSig);
            
            if (!java.util.Arrays.equals(decodedSig, expectedSig)) {
                throw new SecurityException("Bad signature in Facebook signed_request");
            }

            // Parse payload
            String decodedPayload = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
            JsonNode payloadNode = objectMapper.readTree(decodedPayload);

            if (!payloadNode.has("user_id")) {
                throw new IllegalArgumentException("No user_id found in payload");
            }

            String facebookUserId = payloadNode.get("user_id").asText();

            // Perform Deletion
            List<SocialAccount> accountsToDelete = socialAccountRepository.findByFacebookUserId(facebookUserId);
            for (SocialAccount account : accountsToDelete) {
                socialAccountRepository.delete(account);
                logger.info("Deleted Facebook/Instagram account ID {} due to data deletion request", account.getId());
            }

            // Create Confirmation Code
            String confirmationCode = UUID.randomUUID().toString();
            DataDeletionStatus status = DataDeletionStatus.builder()
                    .confirmationCode(confirmationCode)
                    .facebookUserId(facebookUserId)
                    .status("COMPLETED")
                    .build();
            deletionStatusRepository.save(status);

            return confirmationCode;

        } catch (Exception e) {
            logger.error("Error processing Facebook data deletion request", e);
            throw new RuntimeException("Failed to process data deletion request", e);
        }
    }

    private byte[] hmacSha256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] base64UrlDecode(String input) {
        String padded = input.replace("-", "+").replace("_", "/");
        int paddingLength = (4 - padded.length() % 4) % 4;
        padded += "=".repeat(paddingLength);
        return Base64.getDecoder().decode(padded);
    }
}
