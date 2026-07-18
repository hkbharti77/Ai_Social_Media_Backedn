package com.aiplatform.service;

import com.aiplatform.dto.AiRequest;
import com.aiplatform.model.AiModelSelection;
import com.aiplatform.model.ApiProtocol;
import com.aiplatform.model.BrandVoiceMode;
import com.aiplatform.model.BusinessProfile;
import com.aiplatform.model.User;
import com.aiplatform.util.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.List;

/**
 * AiMediaService — Single Responsibility: Image Generation and Visual Context.
 *
 * Orchestrates calls to AI image generation models, formats visual brand identity prompts,
 * normalizes media aspects, and uploads generated files to S3.
 */
@Service
@RequiredArgsConstructor
public class AiMediaService {

    private final Logger logger = LoggerFactory.getLogger(AiMediaService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final S3Service s3Service;
    private final SubscriptionService subscriptionService;
    private final AiCacheService aiCacheService;
    private final ProviderRouter providerRouter;

    @Value("${spring.ai.google.genai.api-key}")
    private String apiKey;

    @Value("${spring.ai.google.genai.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String apiUrl;

    /**
     * Generates an image using AI and uploads it directly to S3.
     */
    public String generateAndUploadImage(String suggestion, String userCommand, Long userId, String modelId, BusinessProfile bp) throws Exception {
        return generateAndUploadImageInternal(suggestion, userCommand, userId, modelId, bp, 0);
    }

    private String generateAndUploadImageInternal(String suggestion, String userCommand, Long userId, String modelId, BusinessProfile bp, int attempt) throws Exception {
        subscriptionService.checkImageStorageLimit(userId);

        AiRequest routerRequest = AiRequest.builder()
                .userId(userId)
                .modelId(modelId)
                .actionType("IMAGE_GENERATION")
                .build();
        String finalModelId = providerRouter.route(routerRequest).selectedModelId();
        AiModelSelection meta = AiModelSelection.fromModelId(finalModelId);
        String suffix = (meta.getProtocol() == ApiProtocol.GEMINI) ? ":generateContent" : ":predict";
        String url = String.format("%s/models/%s%s?key=%s", apiUrl, meta.getActualApiModelId(), suffix, apiKey);

        String rawAspectRatio = (bp.getAspectRatio() != null && !bp.getAspectRatio().isEmpty()) ? bp.getAspectRatio() : "1:1";
        String aspectRatio = normalizeAspectRatio(rawAspectRatio);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("sampleCount", 1);
        parameters.put("aspectRatio", aspectRatio);

        String currentContent = buildVisualContext(bp) + buildBrandVoiceContext(bp, null);
        String cacheId = (meta.getProtocol() == ApiProtocol.GEMINI) ? aiCacheService.resolveGeminiCacheId(bp, modelId, currentContent) : null;

        StringBuilder personaPrompt = new StringBuilder();
        if (userCommand != null && !userCommand.trim().isEmpty()) {
            personaPrompt.append("[User Intent]: ").append(sanitizePrompt(userCommand)).append(". ");
        }

        personaPrompt.append("[Scene]: ").append(sanitizePrompt(suggestion));
        personaPrompt.append(". [Brand Identity]: ");

        if (cacheId == null) {
            personaPrompt.append("As a 'Social Media Branding Photographer' for ").append(bp.getBusinessName()).append(" (Niche: ").append(bp.getNiche()).append("). ");
            personaPrompt.append("Follow this strict style: Style: ").append(bp.getImageStyle()).append(". Mood: ").append(bp.getBrandMood()).append(". Design: ").append(bp.getDesignStyle());
            if (bp.getBrandColors() != null && !bp.getBrandColors().isEmpty()) {
                personaPrompt.append(". Brand Color Palette: ").append(getColorDescription(bp.getBrandColors()));
            }
            if (bp.getPeoplePreference() != null) personaPrompt.append(". People: ").append(bp.getPeoplePreference());
            if (bp.getCompositionStyle() != null) personaPrompt.append(". Composition: ").append(bp.getCompositionStyle());
            if (bp.getSubjectFocus() != null) personaPrompt.append(". Subject Focus: ").append(bp.getSubjectFocus());
            if (bp.getLightingStyle() != null) personaPrompt.append(". Lighting: ").append(bp.getLightingStyle());
            if (bp.getCameraAngle() != null) personaPrompt.append(". Angle: ").append(bp.getCameraAngle());
            if (bp.getColorTemperature() != null) personaPrompt.append(". Color Temperature: ").append(bp.getColorTemperature());
            if (bp.getBackgroundStyle() != null) personaPrompt.append(". Background: ").append(bp.getBackgroundStyle());
        } else {
            personaPrompt.append("[Using Cached Brand Style Persona]");
        }

        StringBuilder guardrails = new StringBuilder(". Avoid: ");
        if (bp.getNegativePrompt() != null && !bp.getNegativePrompt().isEmpty()) {
            guardrails.append(bp.getNegativePrompt()).append(", ");
        }

        if (bp.getTextOverlay() == null || !bp.getTextOverlay().isEnabled()) {
            guardrails.append("text, words, letters, typography, quotes, logos, hex codes, labels, signs, watermarks.");
        }
        guardrails.append(" blurry, distorted, extra limbs, deformed features.");

        personaPrompt.append(guardrails);
        personaPrompt.append("\n\nACTUAL SCENE TO RENDER: ").append(suggestion);

        String enhancedPrompt = personaPrompt.toString();

        Map<String, Object> requestBody = new HashMap<>();
        if (cacheId != null) {
            requestBody.put("cachedContent", cacheId);
        }

        if (meta.getProtocol() == ApiProtocol.GEMINI) {
            requestBody.put("contents", Collections.singletonList(Map.of(
                "parts", Collections.singletonList(Map.of("text", enhancedPrompt))
            )));

            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.putAll(Map.of(
                "responseModalities", Collections.singletonList("IMAGE"),
                "candidateCount", 1
            ));
            requestBody.put("generationConfig", generationConfig);

            List<Map<String, String>> safetySettings = List.of(
                Map.of("category", "HARM_CATEGORY_HATE_SPEECH", "threshold", "BLOCK_NONE"),
                Map.of("category", "HARM_CATEGORY_HARASSMENT", "threshold", "BLOCK_NONE"),
                Map.of("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT", "threshold", "BLOCK_NONE"),
                Map.of("category", "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold", "BLOCK_NONE"),
                Map.of("category", "HARM_CATEGORY_CIVIC_INTEGRITY", "threshold", "BLOCK_NONE")
            );
            requestBody.put("safetySettings", safetySettings);
        } else {
            requestBody.put("instances", Collections.singletonList(Map.of("prompt", enhancedPrompt)));
            requestBody.put("parameters", parameters);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        logger.info("🎨 Dispatching Image Gen [{}] for user {} with 'Persona' enhanced prompt: {}", modelId, userId, enhancedPrompt);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                byte[] imageBytes = null;

                if (meta.getProtocol() == ApiProtocol.GEMINI) {
                    JsonNode candidates = root.path("candidates");
                    if (candidates.isArray() && candidates.size() > 0) {
                        JsonNode parts = candidates.get(0).path("content").path("parts");
                        if (!parts.isMissingNode() && parts.size() > 0) {
                            JsonNode inlineData = parts.get(0).path("inlineData");
                            if (!inlineData.isMissingNode()) {
                                String base64 = inlineData.path("data").asText();
                                imageBytes = Base64.getDecoder().decode(base64);
                            }
                        }
                    }
                } else {
                    JsonNode predictions = root.path("predictions");
                    if (predictions.isArray() && predictions.size() > 0) {
                        String base64 = predictions.get(0).path("bytesBase64Encoded").asText();
                        imageBytes = Base64.getDecoder().decode(base64);
                    }
                }

                if (imageBytes != null) {
                    try {
                        BufferedImage pngImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
                        if (pngImage != null) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            BufferedImage resultImage = new BufferedImage(
                                pngImage.getWidth(),
                                pngImage.getHeight(),
                                BufferedImage.TYPE_INT_RGB
                            );
                            Graphics2D g = resultImage.createGraphics();
                            g.setPaint(Color.WHITE);
                            g.fillRect(0, 0, resultImage.getWidth(), resultImage.getHeight());
                            g.drawImage(pngImage, 0, 0, null);
                            g.dispose();

                            ImageIO.write(resultImage, "jpg", baos);
                            imageBytes = baos.toByteArray();
                        }
                    } catch (Exception e) {
                        logger.warn("⚠️ Failed to convert AI image to JPEG, falling back to PNG: {}", e.getMessage());
                    }

                    String fileName = "ai_image_" + UUID.randomUUID() + ".jpg";
                    try (InputStream is = new ByteArrayInputStream(imageBytes)) {
                        String resultUrl = s3Service.uploadFile(fileName, is, userId, true);
                        logger.info("✅ Image Gen successful (JPEG): {}", resultUrl);
                        subscriptionService.incrementImageStorage(userId);
                        return resultUrl;
                    }
                } else {
                    String reason = root.path("candidates").get(0).path("finishReason").asText();
                    logger.warn("⚠️ Image Gen response was 200 OK but imageBytes is NULL. Finish Reason: {}. Body: {}", reason, root.toString());

                    if (("SAFETY".equals(reason) || "NO_IMAGE".equals(reason)) && attempt < 1) {
                         logger.info("🔄 Re-attempting Image Gen with Simplified Artistic Prompt due to {} block...", reason);
                         String fallbackCommand = "A beautiful artistic visual representation of " + suggestion;
                         return generateAndUploadImageInternal(suggestion, fallbackCommand, userId, modelId, bp, attempt + 1);
                    }

                    if ("SAFETY".equals(reason) || "NO_IMAGE".equals(reason)) {
                         throw new RuntimeException("AI blocked image generation due to safety filters or complex prompt. Try a simpler description.");
                    }
                }
            }
            throw new RuntimeException("Image generation failed status: " + response.getStatusCode());
        } catch (HttpStatusCodeException e) {
            String errorBody = e.getResponseBodyAsString();
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS || errorBody.contains("429")) {
                if (attempt < 1) {
                    logger.warn("⚠️ AI Quota hit. Entering Emergency Wait (5s) for retry [Attempt {}]", attempt + 1);
                    throttle(5000);
                    return generateAndUploadImageInternal(suggestion, userCommand, userId, modelId, bp, attempt + 1);
                }
                logger.error("❌ AI Quota Exceeded (429) during image generation after retry.");
                throw new RuntimeException("AI API Quota Exceeded. Please wait 60 seconds and try again.");
            }
            logger.error("❌ Image Generation API Error: {} - Body: {}", e.getStatusCode(), errorBody);
            throw new RuntimeException("AI API Error: " + e.getStatusCode() + " - " + errorBody);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("429")) {
                if (attempt < 1) {
                    throttle(5000);
                    return generateAndUploadImageInternal(suggestion, userCommand, userId, modelId, bp, attempt + 1);
                }
                throw new RuntimeException("AI API Quota Exceeded. Please try again in 60 seconds.");
            }
            logger.error("❌ Image Generation Exception: {}", e.getMessage(), e);
            throw e;
        }
    }

    public String buildVisualContext(BusinessProfile bp) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 🏛️ BRAND IDENTITY BIBLE\n");
        appendIfPresent(sb, "Business Name", bp.getBusinessName());
        appendIfPresent(sb, "Niche/Description", bp.getNiche());
        appendIfPresent(sb, "Brand Mood", bp.getBrandMood());
        appendIfPresent(sb, "Design Style", bp.getDesignStyle());

        if (bp.getBrandColors() != null && !bp.getBrandColors().isEmpty()) {
            sb.append("- Strict Brand Color Palette: ")
              .append(getColorDescription(bp.getBrandColors()))
              .append(" (Use these as the dominant aesthetic theme. DO NOT render literal hex codes or text of these colors)\n");
        }

        sb.append("\n### 📸 ART DIRECTION & DESIGN GUIDE\n");
        appendIfPresent(sb, "Image Style", bp.getImageStyle());
        appendIfPresent(sb, "Image Type", bp.getImageType());
        appendIfPresent(sb, "People Preference", bp.getPeoplePreference());
        appendIfPresent(sb, "Composition Style", bp.getCompositionStyle());
        appendIfPresent(sb, "Subject Focus", bp.getSubjectFocus());
        appendIfPresent(sb, "Camera Angle", bp.getCameraAngle());
        appendIfPresent(sb, "Lighting Style", bp.getLightingStyle());
        appendIfPresent(sb, "Color Temperature", bp.getColorTemperature());
        appendIfPresent(sb, "Background Style", bp.getBackgroundStyle());

        sb.append("\n### 🛠️ TECHNICAL CONSTRAINTS\n");
        if (bp.getTextOverlay() != null && bp.getTextOverlay().isEnabled()) {
            sb.append("- Text Overlay: REQUIRED (Style: ").append(bp.getTextOverlay().getStyle())
              .append(", Position: ").append(bp.getTextOverlay().getPosition()).append(")\n");
        }
        appendIfPresent(sb, "Logo Placement", bp.getLogoPlacement());
        appendIfPresent(sb, "Aspect Ratio", normalizeAspectRatio(bp.getAspectRatio()));
        appendIfPresent(sb, "Quality Level", bp.getQualityLevel());
        appendIfPresent(sb, "Visual Constraints (NO-GOs)", bp.getVisualConstraints());
        appendIfPresent(sb, "Negative Prompt", bp.getNegativePrompt());

        return sb.length() > 0 ? sb.toString() : "Standard professional brand visuals.";
    }

    private void appendIfPresent(StringBuilder sb, String label, Object value) {
        if (value != null && !value.toString().trim().isEmpty()) {
            sb.append("- ").append(label).append(": ").append(value).append("\n");
        }
    }

    public String buildBrandVoiceContext(BusinessProfile bp, String modeStr) {
        BrandVoiceMode mode = BrandVoiceMode.NONE;
        try {
            if (modeStr != null) mode = BrandVoiceMode.valueOf(modeStr.toUpperCase());
        } catch (Exception e) {
            mode = bp.getDefaultVoiceMode() != null ? bp.getDefaultVoiceMode() : BrandVoiceMode.NONE;
        }

        if (mode == BrandVoiceMode.NONE) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n### 🎭 BRAND VOICE & PERSONAL STYLE\n");

        if (mode == BrandVoiceMode.STYLE_DNA && bp.getBrandStyleDna() != null) {
            sb.append("Style Persona: ").append(bp.getBrandStyleDna()).append("\n");
        } else if (mode == BrandVoiceMode.FULL_CONTEXT) {
            if (bp.getBrandStyleDna() != null) {
                sb.append("Style Persona: ").append(bp.getBrandStyleDna()).append("\n");
            }
            if (bp.getBrandVoiceSamples() != null && !bp.getBrandVoiceSamples().isEmpty()) {
                sb.append("User's Past Writing Samples (Strictly mimic this tone):\n");
                for (int i = 0; i < Math.min(bp.getBrandVoiceSamples().size(), 5); i++) {
                    sb.append("- ").append(bp.getBrandVoiceSamples().get(i)).append("\n");
                }
            }
        }

        return sb.toString();
    }

    public String sanitizePrompt(String prompt) {
        if (prompt == null) return "";
        String cleaned = prompt.replaceAll("(?i)\\b(photorealistic|hyperrealistic|4k resolution|8k resolution|masterpiece|trending on artstation|unreal engine|octane render|high definition)\\b", "");
        cleaned = cleaned.replaceAll("#[a-fA-F0-9]{3,6}", "");
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    private String getColorDescription(List<String> hexCodes) {
        if (hexCodes == null || hexCodes.isEmpty()) return "Natural colors";
        List<String> readableColors = new ArrayList<>();
        for (String hex : hexCodes) {
            String name = mapHexToColorName(hex);
            if (!name.equals("unknown")) {
                readableColors.add(name);
            }
        }
        return readableColors.isEmpty() ? "Brand colors" : String.join(", ", readableColors);
    }

    private String mapHexToColorName(String hex) {
        if (hex == null) return "unknown";
        hex = hex.toUpperCase().replace("#", "");

        if (hex.startsWith("FF0000")) return "Vibrant Red";
        if (hex.startsWith("00FF00")) return "Lime Green";
        if (hex.startsWith("0000FF")) return "Royal Blue";
        if (hex.startsWith("FFFFFF")) return "Pure White";
        if (hex.startsWith("000000")) return "Deep Black";
        if (hex.startsWith("FFFF00")) return "Bright Yellow";
        if (hex.startsWith("FFA500")) return "Orange";
        if (hex.startsWith("800080")) return "Purple";
        if (hex.startsWith("FFC0CB")) return "Pink";
        if (hex.startsWith("808080")) return "Slate Grey";
        if (hex.startsWith("A52A2A")) return "Brown";
        if (hex.startsWith("D4AF37")) return "Golden";
        if (hex.startsWith("C0C0C0")) return "Silver";

        return "Brand specific color";
    }

    public String normalizeAspectRatio(String ratio) {
        if (ratio == null) return "1:1";

        return switch (ratio.trim()) {
            case "1:1", "SQUARE" -> "1:1";
            case "9:16", "VERTICAL", "STORY", "REEL" -> "9:16";
            case "16:9", "LANDSCAPE", "CINEMA" -> "16:9";
            case "4:3" -> "4:3";
            case "3:4", "4:5", "PORTRAIT" -> "3:4";
            case "1.91:1" -> "16:9";
            default -> "1:1";
        };
    }

    public void throttle(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
