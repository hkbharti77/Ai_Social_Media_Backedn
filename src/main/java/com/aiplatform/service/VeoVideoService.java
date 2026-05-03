package com.aiplatform.service;

import com.aiplatform.dto.ContentGenerationDtos.*;
import com.aiplatform.exception.VeoGenerationException;
import com.aiplatform.exception.VeoRateLimitException;
import com.aiplatform.exception.VeoTimeoutException;
import com.aiplatform.model.BusinessProfile;
import com.aiplatform.model.VeoModelSelection;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aiplatform.model.AiUsageLog;
import com.aiplatform.repository.AiUsageLogRepository;
import com.aiplatform.repository.UserRepository;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import java.time.LocalDateTime;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VeoVideoService {
    private static final Logger logger = LoggerFactory.getLogger(VeoVideoService.class);

    private final RestTemplate restTemplate;
    private final S3Service s3Service;
    private final ChatClient chatClient;
    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final UserRepository userRepository;

    @Value("${spring.ai.imagen.api-url}")
    private String apiBaseUrl;

    @Value("${spring.ai.google.genai.api-key}")
    private String apiKey;

    private static final int POLL_INTERVAL_SECONDS = 5;
    private static final int MAX_POLL_ATTEMPTS = 24;

    public VideoGenerationResponse generateVideo(BusinessProfile profile, PostGenerationRequest request, Long userId) {
        // Determine which Veo model to use based on user selection
        String selectedModelId = request.getVideoModelId() != null ? request.getVideoModelId() : "veo-fast";
        VeoModelSelection veoModel = VeoModelSelection.fromModelId(selectedModelId);
        
        logger.info("Starting Veo video generation for user: {}. Model: {}, Aspect Ratio: {}", 
                    userId, veoModel.getModelId(), request.getAspectRatio());
        
        String enrichedPrompt = enrichPrompt(request.getCommand(), profile);
        String operationName = submitGenerationJob(enrichedPrompt, request.getAspectRatio(), veoModel);
        
        String completedOperationJson = pollForCompletion(operationName);
        
        byte[] videoBytes = extractVideoBytes(completedOperationJson);
        
        String fileName = String.format("veo_%d_%s.mp4", userId, UUID.randomUUID().toString());
        logger.info("Initiating S3 upload for video: {}", fileName);
        
        try {
            // Upload with public-read ACL so Facebook/Instagram can fetch the video URL directly
            // without pre-signed query parameters (which Facebook rejects with error 389)
            String videoUrl = s3Service.uploadFile(fileName, new ByteArrayInputStream(videoBytes), userId, true);
            logger.info("S3 upload successful. Video URL: {}", videoUrl);
            
            subscriptionService.incrementVideoStorage(userId);
            
            logger.info("Starting AI metadata generation (Caption/Hashtags) for reel...");
            ReelResponse reelContent = generateReelContent(request.getCommand(), profile, userId);
            logger.info("Metadata generation complete.");
            
            VideoGenerationResponse response = VideoGenerationResponse.builder()
                    .videoUrl(videoUrl)
                    .caption(reelContent.getCaption())
                    .hashtags(reelContent.getHashtags())
                    .videoScript(reelContent.getVideoScript())
                    .audioSuggestion(reelContent.getAudioSuggestion())
                    .imageUrl(reelContent.getImageUrl())
                    .generationMode("VEO_ACTUAL")
                    .modelUsed(veoModel.getModelId())
                    .creditsUsed(veoModel.getCreditCost())
                    .build();

            // Audit the video generation
            logUsage(userId, veoModel.getActualApiModelId(), "VEO_GENERATION", null, enrichedPrompt, videoUrl);
            
            return response;
        } catch (Exception e) {
            logger.error("Critical failure during post-generation flow (S3/Metadata): {}", e.getMessage());
            throw new VeoGenerationException("Post-generation processing failed: " + e.getMessage());
        }
    }

    private String enrichPrompt(String userPrompt, BusinessProfile profile) {
        return String.format("[Business: %s, Niche: %s, Audience: %s] %s",
                profile.getBusinessName() != null ? profile.getBusinessName() : "Unknown",
                profile.getNiche() != null ? profile.getNiche() : "Unknown",
                profile.getTargetAudience() != null ? profile.getTargetAudience() : "General",
                userPrompt);
    }

    private String submitGenerationJob(String enrichedPrompt, String aspectRatio, VeoModelSelection veoModel) {
        String url = String.format("%s/models/%s:predictLongRunning", apiBaseUrl, veoModel.getActualApiModelId());
        
        Map<String, Object> instance = new HashMap<>();
        instance.put("prompt", enrichedPrompt);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("aspectRatio", aspectRatio != null ? aspectRatio : "9:16");
        parameters.put("durationSeconds", 8);
        parameters.put("resolution", "1080p");
        parameters.put("sampleCount", 1);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("instances", java.util.List.of(instance));
        requestBody.put("parameters", parameters);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            logger.info("Submitting Veo request to Google Veo API for model: {}", veoModel.getActualApiModelId());
            
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, entity, JsonNode.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String opName = response.getBody().get("name").asText();
                logger.info("Veo job submitted successfully. Operation: {}", opName);
                return opName;
            }
            throw new VeoGenerationException("Failed to submit generation job: " + response.getStatusCode());
        } catch (HttpClientErrorException.TooManyRequests e) {
            logger.error("Veo API Rate Limit reached (429)");
            throw new VeoRateLimitException("Veo API rate limit reached (429).");
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            String errorBody = (e instanceof HttpClientErrorException) ? 
                ((HttpClientErrorException) e).getResponseBodyAsString() : 
                ((HttpServerErrorException) e).getResponseBodyAsString();
            logger.error("Veo API Error ({}): {}", e.getStatusCode(), errorBody);
            throw new VeoGenerationException("Veo API error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during Veo job submission", e);
            throw new VeoGenerationException("Unexpected error during job submission: " + e.getMessage());
        }
    }

    private String pollForCompletion(String operationName) {
        String url = String.format("%s/%s", apiBaseUrl, operationName);
        String requestUrl = url + "?key=" + apiKey;
        
        for (int i = 0; i < MAX_POLL_ATTEMPTS; i++) {
            try {
                logger.info("Polling Veo operation (Attempt {}/{}): {}", i + 1, MAX_POLL_ATTEMPTS, operationName);
                Thread.sleep(POLL_INTERVAL_SECONDS * 1000L);
                
                ResponseEntity<String> response = restTemplate.getForEntity(requestUrl, String.class);
                
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    if (root.has("done") && root.get("done").asBoolean()) {
                        if (root.has("error")) {
                            logger.error("Veo job {} failed with error: {}", operationName, root.get("error"));
                            throw new VeoGenerationException("Video generation failed: " + root.get("error").get("message").asText());
                        }
                        logger.info("Veo generation job completed successfully: {}", operationName);
                        logger.debug("Completed Operation JSON Structure: {}", response.getBody());
                        return response.getBody();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new VeoGenerationException("Polling interrupted.");
            } catch (Exception e) {
                logger.warn("Polling attempt {}/{} failed for {}: {}", i + 1, MAX_POLL_ATTEMPTS, operationName, e.getMessage());
            }
        }
        throw new VeoTimeoutException("Video generation timed out after " + (POLL_INTERVAL_SECONDS * MAX_POLL_ATTEMPTS) + " seconds");
    }

    private byte[] extractVideoBytes(String completedOperationJson) {
        try {
            JsonNode root = objectMapper.readTree(completedOperationJson);
            JsonNode responseNode = root.path("response");
            
            // Format A: response.generateVideoResponse.generatedSamples[0].video... (Veo 3.1)
            JsonNode generateVideoResponse = responseNode.path("generateVideoResponse");
            JsonNode sample = null;
            
            if (!generateVideoResponse.isMissingNode()) {
                sample = generateVideoResponse.path("generatedSamples").get(0);
            } else {
                // Format B: response.generatedSamples[0].video... (Fallback)
                sample = responseNode.path("generatedSamples").get(0);
            }

            JsonNode videoNode = null;
            if (sample != null && !sample.isMissingNode()) {
                videoNode = sample.path("video");
            } else {
                // Format C: response.video... (direct check)
                videoNode = responseNode.path("video");
            }

            if (videoNode == null || videoNode.isMissingNode()) {
                throw new VeoGenerationException("Could not find video data in API response. Response may be incomplete.");
            }

            // Check for Base64 Bytes
            String base64Bytes = videoNode.path("bytesBase64Encoded").asText("");
            if (!base64Bytes.isEmpty()) {
                logger.info("Successfully extracted video bytes from Base64 data.");
                return Base64.getDecoder().decode(base64Bytes);
            }

            // Check for URI
            String uri = videoNode.path("uri").asText("");
            if (!uri.isEmpty()) {
                logger.info("Video data found at URI: {}. Fetching content...", uri);
                byte[] bytes = fetchBytesFromUri(uri);
                logger.info("Successfully fetched {} bytes from URI.", bytes.length);
                return bytes;
            }

            throw new VeoGenerationException("API response contains neither bytesBase64Encoded nor a valid download URI.");
        } catch (VeoGenerationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to extract video bytes from JSON: {}", e.getMessage());
            throw new VeoGenerationException("Failed to extract video bytes: " + e.getMessage());
        }
    }

    private byte[] fetchBytesFromUri(String uri) {
        try {
            if (!uri.startsWith("http")) {
                 throw new VeoGenerationException("Unsupported URI format for video download: " + uri);
            }
            
            // The download URI from Google usually needs the API key for authentication.
            String downloadUrl = uri;
            if (!uri.contains("key=")) {
                String separator = uri.contains("?") ? "&" : "?";
                downloadUrl = uri + separator + "key=" + apiKey;
            }
            
            logger.info("Fetching video bytes from Google Storage...");
            ResponseEntity<byte[]> response = restTemplate.getForEntity(downloadUrl, byte[].class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }
            throw new VeoGenerationException("Failed to fetch video from URI. Status: " + response.getStatusCode());
        } catch (Exception e) {
            logger.error("Error fetching video bytes from URI: {}", e.getMessage());
            throw new VeoGenerationException("Error fetching video content from URI: " + e.getMessage());
        }
    }

    private ReelResponse generateReelContent(String prompt, BusinessProfile profile, Long userId) {
        String template = """
                You are a social media expert. Based on this prompt: "%s"
                For business: %s (Niche: %s, Audience: %s)
                Generate a catchy caption, hashtags, a short video script, and music mood.
                Return ONLY a JSON object matching this structure:
                {
                  "caption": "string",
                  "hashtags": ["string", "string"],
                  "videoScript": "string (NOT an array)",
                  "audioSuggestion": "string"
                }
                """;
        
        String chatPrompt = String.format(template, prompt, 
                profile.getBusinessName(), profile.getNiche(), profile.getTargetAudience());
        
        try {
            ChatResponse chatResponse = chatClient.prompt(new Prompt(chatPrompt)).call().chatResponse();
            String response = chatResponse.getResult().getOutput().getText();
            Usage usage = chatResponse.getMetadata().getUsage();
            
            logger.debug("LLM Metadata Response: {}", response);
            
            // Basic JSON extraction if LLM wraps it in markdown
            if (response.contains("```json")) {
                response = response.substring(response.indexOf("```json") + 7, response.lastIndexOf("```")).trim();
            } else if (response.contains("```")) {
                response = response.substring(response.indexOf("```") + 3, response.lastIndexOf("```")).trim();
            }

            // Audit metadata generation
            logUsage(userId, "gemini-1.5-flash", "REEL_METADATA", usage, chatPrompt, null);
            
            return objectMapper.readValue(response, ReelResponse.class);
        } catch (Exception e) {
            logger.error("Failed to generate reel content. Error: {}. Check if LLM returned an array for videoScript.", e.getMessage());
            ReelResponse fallback = new ReelResponse();
            fallback.setCaption("Check out our latest video!");
            fallback.setHashtags(java.util.List.of("trending", "ai"));
            fallback.setVideoScript("Professional AI generated video.");
            return fallback;
        }
    }

    private void logUsage(Long userId, String modelId, String actionType, Usage usage, String prompt, String resultUrl) {
        try {
            userRepository.findById(userId).ifPresent(user -> {
                AiUsageLog log = AiUsageLog.builder()
                        .user(user)
                        .modelId(modelId)
                        .actionType(actionType)
                        .promptTokens(usage != null ? (int) usage.getPromptTokens() : 0)
                        .completionTokens(usage != null ? (int) usage.getCompletionTokens() : 0)
                        .totalTokens(usage != null ? (int) usage.getTotalTokens() : 0)
                        .prompt(prompt)
                        .resultUrl(resultUrl)
                        .featureName("Reel Generator")
                        .createdAt(LocalDateTime.now())
                        .build();
                aiUsageLogRepository.save(log);
            });
        } catch (Exception e) {
            logger.error("Failed to log Veo/Metadata usage: {}", e.getMessage());
        }
    }
}
