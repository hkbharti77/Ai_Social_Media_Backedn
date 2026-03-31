package com.aiplatform.service;

import com.aiplatform.dto.ContentGenerationDtos.*;
import com.aiplatform.model.AiModelSelection;
import com.aiplatform.model.ApiProtocol;
import com.aiplatform.model.BusinessProfile;
import com.aiplatform.util.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiContentService {
    private static final Logger logger = LoggerFactory.getLogger(AiContentService.class);

    private final ChatClient chatClient;
    private final RestTemplate restTemplate;
    private final S3Service s3Service;
    private final ObjectMapper objectMapper;
    private final SubscriptionService subscriptionService;
    private final DistributedLockService lockService;

    @Value("${spring.ai.google.genai.api-key}")
    private String apiKey;

    @Value("${spring.ai.imagen.api-url}")
    private String apiUrl;

    @Value("${spring.ai.imagen.image-model}")
    private String imageModel;

    private static final String CAPTION_TEMPLATE = """
            You are a social media expert. Create a {tone} post for {businessName}, a {niche} brand.
            Target audience: {audience}.
            Preferred Hashtags to include: {preferredHashtags}
            User instruction: {command}.
            
            Visual Brand Identity & Constraints:
            {visualContext}
            
            Return a JSON object exactly like this structure:
            {jsonStructure}
            
            Return ONLY valid JSON wrapped in curly braces. Caption max 280 chars. 
            Crucial: The 'imageSuggestion' must NOT include technical labels, buzzwords, or hex codes (e.g., #FFFFFF). 
            Instead, refer to colors by name (e.g., 'warm gold', 'midnight blue').
            The 'imageSuggestion' MUST describe a scene, NOT text. Do NOT include words or quotes for the image to render.
            """;
    
    private static final String GAP_ANALYSIS_TEMPLATE = """
            You are a high-level B2B Growth Strategy Consultant for "{senderName}" (Experts in {senderNiche}).
            Analyze the {targetType} industry in {city} for the audience: {targetAudience}.
            Identify 5 specific "Strategic Gaps" in their business where "{senderName}"'s {senderNiche} solutions can solve their problems and grow their ROI.
            
            Return JSON format exactly like this structure:
            {jsonStructure}
            """;

    private static final String CONTENT_STRATEGY_TEMPLATE = """
            You are a social media strategist for a {businessType} in {city}.
            Analyze what content topics this type of business typically misses for the target audience: {targetAudience}.
            Give 5 specific post ideas their competitors are NOT doing. Each idea should be highly specific, actionable and creative.
            
            Return JSON format exactly like this structure:
            {jsonStructure}
            """;

    private static final String PERFORMANCE_PREDICTOR_TEMPLATE = """
            You are a social media expert. Score this post out of 100.
            
            Business Type: {businessType}
            Target Audience: {targetAudience}
            Brand Tone: {brandTone}
            Post Draft: "{postDraft}"
            
            Return JSON format exactly like this structure:
            {jsonStructure}
            """;

    private static final String REVIEW_REPLY_TEMPLATE = """
            You are a professional customer support butler for a {businessName}. 
            A customer left a {rating}-star review: "{reviewText}"
            
            Draft a polite, professional, and personalized reply. 
            If the rating is low (1-3), be empathetic and offer support. 
            If the rating is high (4-5), express gratitude.
            
            Return ONLY the reply text, max 300 chars.
            """;


    public GeneratedPost generatePost(BusinessProfile bp, String userCmd, Long userId, String modelId) {
        // 1. Model Metadata & Defaulting
        String finalModelId = (modelId != null && !modelId.isEmpty()) ? modelId : 
                             SecurityUtils.getCurrentUser().get().getSubscriptionTier().getDefaultImageModel();
        
        // 2. Credits check using the specific model cost
        lockService.executeWithLock("credits:" + userId, Duration.ofSeconds(5), Duration.ofSeconds(10), () -> {
            subscriptionService.checkAndDecrementCredits(userId, finalModelId, "AI Content Generation (" + finalModelId + ")");
            return null;
        });

        String visualContext = buildVisualContext(bp);
        PromptTemplate pt = new PromptTemplate(CAPTION_TEMPLATE);
        Prompt prompt = pt.create(Map.of(
                "businessName", bp.getBusinessName() != null ? bp.getBusinessName() : "our brand",
                "niche", bp.getNiche() != null ? bp.getNiche() : "generic",
                "tone", bp.getBrandTone() != null ? bp.getBrandTone() : "professional",
                "audience", bp.getTargetAudience() != null ? bp.getTargetAudience() : "general",
                "preferredHashtags", bp.getPreferredHashtags() != null ? bp.getPreferredHashtags() : "",
                "command", userCmd,
                "visualContext", visualContext,
                "jsonStructure", "{\"caption\": \"...\", \"hashtags\": [\"#...\", \"...\"], \"imageSuggestion\": \"Detailed description of a professional photo or graphic...\"}"
        ));

        logger.info("🚀 Generating AI post [Model: {}] for user: {}", finalModelId, userId);

        String content;
        try {
            // Link creativity level (0.0 - 1.0) to model temperature
            double temperature = (bp.getCreativityLevel() != null) ? bp.getCreativityLevel() : 0.7;
            
            content = chatClient.prompt(prompt)
                    .options(GoogleGenAiChatOptions.builder()
                            .temperature(temperature)
                            .build())
                    .call()
                    .content();
        } catch (Exception e) {
            logger.error("❌ AI Chat Generation failed: {}", e.getMessage(), e);
            if (e.getMessage().contains("429") || e.getMessage().toLowerCase().contains("quota")) {
                throw new RuntimeException("AI Quota Exceeded. Please wait 60 seconds and try again.", e);
            }
            throw new RuntimeException("AI Generation failed.", e);
        }

        try {
            // Basic extraction in case LLM adds markdown blocks
            if (content.contains("```json")) {
                content = content.substring(content.indexOf("```json") + 7, content.lastIndexOf("```"));
            } else if (content.contains("```")) {
                content = content.substring(content.indexOf("```") + 3, content.lastIndexOf("```"));
            }
            
            GeneratedPost generatedPost = objectMapper.readValue(content, GeneratedPost.class);
            
            // 3. Media Generation (Image)
            if (generatedPost.getImageSuggestion() != null && !generatedPost.getImageSuggestion().isEmpty()) {
                try {
                    String imageUrl = generateAndUploadImage(generatedPost.getImageSuggestion(), userCmd, userId, finalModelId, bp);
                    generatedPost.setImageUrl(imageUrl);
                } catch (Exception e) {
                    logger.error("❌ Failed to generate AI image: {}", e.getMessage());
                }
            }
            
            return generatedPost;
        } catch (Exception e) {
            logger.error("❌ Failed to parse GeneratedPost: " + content, e);
            throw new RuntimeException("AI Content processing failed.");
        }
    }

    public ContentGapResponse generateGapAnalysis(ContentGapRequest request, BusinessProfile sender) {
        PromptTemplate pt = new PromptTemplate(GAP_ANALYSIS_TEMPLATE);
        Prompt prompt = pt.create(Map.of(
                "senderName", sender.getBusinessName() != null ? sender.getBusinessName() : "our team",
                "senderNiche", sender.getNiche() != null ? sender.getNiche() : "automation",
                "targetType", request.getBusinessType(),
                "city", request.getCity(),
                "targetAudience", request.getTargetAudience(),
                "jsonStructure", "{\"ideas\": [{\"topic\": \"...\", \"whyItWorks\": \"...\", \"sampleCaption\": \"...\"}]}"
        ));

        String content;
        try {
            content = chatClient.prompt(prompt).call().content();
        } catch (Exception e) {
            logger.error("AI Analysis failed: {}", e.getMessage(), e);
            if (e.getMessage().contains("429") || e.getMessage().toLowerCase().contains("quota")) {
                throw new RuntimeException("AI Analysis Quota Exceeded. Please wait 60 seconds and try again.", e);
            }
            throw new RuntimeException("AI Content Analysis failed.");
        }

        try {
            if (content.contains("```json")) {
                content = content.substring(content.indexOf("```json") + 7, content.lastIndexOf("```"));
            } else if (content.contains("```")) {
                content = content.substring(content.indexOf("```") + 3, content.lastIndexOf("```"));
            }
            return objectMapper.readValue(content, ContentGapResponse.class);
        } catch (Exception e) {
            logger.error("❌ Failed to parse gap analysis: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse AI gap analysis response.");
        }
    }

    public ContentGapResponse generateContentStrategy(BusinessProfile bp) {
        PromptTemplate pt = new PromptTemplate(CONTENT_STRATEGY_TEMPLATE);
        Prompt prompt = pt.create(Map.of(
                "businessType", bp.getNiche() != null ? bp.getNiche() : "business",
                "city", "global",
                "targetAudience", bp.getTargetAudience() != null ? bp.getTargetAudience() : "general audience",
                "jsonStructure", "{\"ideas\": [{\"topic\": \"...\", \"whyItWorks\": \"...\", \"sampleCaption\": \"...\"}]}"
        ));

        String content;
        try {
            content = chatClient.prompt(prompt).call().content();
        } catch (Exception e) {
            logger.error("❌ Content Strategy failed: {}", e.getMessage(), e);
            if (e.getMessage().contains("429") || e.getMessage().toLowerCase().contains("quota")) {
                throw new RuntimeException("AI Quota Exceeded. Please wait 60 seconds and try again.", e);
            }
            throw new RuntimeException("AI Content Strategy failed.");
        }

        try {
            if (content.contains("```json")) {
                content = content.substring(content.indexOf("```json") + 7, content.lastIndexOf("```"));
            } else if (content.contains("```")) {
                content = content.substring(content.indexOf("```") + 3, content.lastIndexOf("```"));
            }
            return objectMapper.readValue(content, ContentGapResponse.class);
        } catch (Exception e) {
            logger.error("❌ Failed to parse content strategy: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse AI content strategy response.");
        }
    }

    public JsonNode predictPerformance(String draft, BusinessProfile bp) {
        PromptTemplate pt = new PromptTemplate(PERFORMANCE_PREDICTOR_TEMPLATE);
        Prompt prompt = pt.create(Map.of(
                "businessType", bp.getNiche() != null ? bp.getNiche() : "generic",
                "targetAudience", bp.getTargetAudience() != null ? bp.getTargetAudience() : "general",
                "brandTone", bp.getBrandTone() != null ? bp.getBrandTone() : "professional",
                "postDraft", draft,
                "jsonStructure", "{\"score\": 85, \"strengths\": [\"...\"], \"improvements\": [\"...\"], \"predicted_outcome\": \"...\"}"
        ));

        String content;
        try {
            content = chatClient.prompt(prompt).call().content();
        } catch (Exception e) {
            logger.error("AI Performance Prediction failed: {}", e.getMessage(), e);
            if (e.getMessage().contains("429") || e.getMessage().toLowerCase().contains("quota")) {
                throw new RuntimeException("AI Prediction Quota Exceeded. Please wait 60 seconds and try again.", e);
            }
            throw new RuntimeException("AI Performance Prediction failed.");
        }

        try {
            if (content.contains("```json")) {
                content = content.substring(content.indexOf("```json") + 7, content.lastIndexOf("```"));
            } else if (content.contains("```")) {
                content = content.substring(content.indexOf("```") + 3, content.lastIndexOf("```"));
            }
            return objectMapper.readTree(content);
        } catch (Exception e) {
            logger.error("Failed to parse Performance Prediction: {}", content);
            throw new RuntimeException("AI Prediction failed to parse.");
        }
    }

    private String generateAndUploadImage(String suggestion, String userCommand, Long userId, String modelId, BusinessProfile bp) throws Exception {
        AiModelSelection meta = AiModelSelection.fromModelId(modelId);
        String suffix = (meta.getProtocol() == ApiProtocol.GEMINI) ? ":generateContent" : ":predict";
        String url = String.format("%s/models/%s%s?key=%s", apiUrl, meta.getActualApiModelId(), suffix, apiKey);

        // --- 1. Synthesize Universal Brand Persona & Parameters ---
        String aspectRatio = (bp.getAspectRatio() != null && !bp.getAspectRatio().isEmpty()) ? bp.getAspectRatio() : "1:1";
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("sampleCount", 1);
        parameters.put("aspectRatio", aspectRatio);

        // --- 2. Construct Prompt: User Command + AI Suggestion + Business Profile ---
        StringBuilder personaPrompt = new StringBuilder();
        
        // 2a. Lead with the user's original command as the PRIMARY creative direction
        if (userCommand != null && !userCommand.trim().isEmpty()) {
            personaPrompt.append("[User Intent]: ").append(sanitizePrompt(userCommand)).append(". ");
        }
        
        // 2b. Layer on AI's visual scene description
        personaPrompt.append("[Scene]: ").append(sanitizePrompt(suggestion));
        
        // 2c. Layer on Business Profile brand identity
        personaPrompt.append(". [Brand Identity]: ");
        if (bp.getBusinessName() != null) personaPrompt.append("For ").append(bp.getBusinessName()).append(". ");
        if (bp.getNiche() != null) personaPrompt.append("Industry: ").append(bp.getNiche()).append(". ");
        personaPrompt.append("Mood: ").append(bp.getBrandMood() != null ? bp.getBrandMood() : "Professional");
        personaPrompt.append(". Design Style: ").append(bp.getDesignStyle() != null ? bp.getDesignStyle() : "Modern");
        if (bp.getBrandColors() != null && !bp.getBrandColors().isEmpty()) {
            personaPrompt.append(". Brand Color Palette: ").append(getColorDescription(bp.getBrandColors()));
        }
        
        // 2d. Layer on Art Direction from profile
        if (bp.getImageStyle() != null) personaPrompt.append(". Image Style: ").append(bp.getImageStyle());
        if (bp.getImageType() != null) personaPrompt.append(". Image Type: ").append(bp.getImageType());
        if (bp.getPeoplePreference() != null) personaPrompt.append(". People: ").append(bp.getPeoplePreference());
        if (bp.getCompositionStyle() != null) personaPrompt.append(". Composition: ").append(bp.getCompositionStyle());
        if (bp.getSubjectFocus() != null) personaPrompt.append(". Subject Focus: ").append(bp.getSubjectFocus());
        if (bp.getLightingStyle() != null) personaPrompt.append(". Lighting: ").append(bp.getLightingStyle());
        if (bp.getCameraAngle() != null) personaPrompt.append(". Angle: ").append(bp.getCameraAngle());
        if (bp.getColorTemperature() != null) personaPrompt.append(". Color Temperature: ").append(bp.getColorTemperature());
        if (bp.getBackgroundStyle() != null) personaPrompt.append(". Background: ").append(bp.getBackgroundStyle());
        
        // 2e. Append Negative Prompt guardrails to ensure clean image
        StringBuilder guardrails = new StringBuilder(". Avoid: ");
        if (bp.getNegativePrompt() != null && !bp.getNegativePrompt().isEmpty()) {
            guardrails.append(bp.getNegativePrompt()).append(", ");
        }
        
        // Block text/hex codes if no text overlay is enabled
        if (bp.getTextOverlay() == null || !bp.getTextOverlay().isEnabled()) {
            guardrails.append("TEXT, WORDS, LETTERS, TYPOGRAPHY, QUOTES, LOGOS, HEX CODES, LABELS, CAPTIONS, SIGNS, SUBTITLES, WATERMARKS, SIGNATURES. ");
        }
        guardrails.append("BLURRY, DISTORTED, EXTRA LIMBS, DEFORMED FEATURES.");
        
        personaPrompt.append(guardrails);
        
        String enhancedPrompt = personaPrompt.toString();

        // --- 3. Construct Protocol-Specific Request ---
        Map<String, Object> requestBody = new HashMap<>();
        if (meta.getProtocol() == ApiProtocol.GEMINI) {
            // For Gemini models, we append constraints to the text prompt as it weights context
            StringBuilder geminiBody = new StringBuilder(enhancedPrompt);
            geminiBody.append("\n\n[Brand Constraints]:");
            geminiBody.append("\n- Aspect Ratio: ").append(aspectRatio);
            
            requestBody.put("contents", Collections.singletonList(Map.of(
                "parts", Collections.singletonList(Map.of("text", geminiBody.toString()))
            )));
            
            // --- 4. Add Image Generation Config for 2026 Gemini Models ---
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.putAll(Map.of(
                "responseModalities", Collections.singletonList("IMAGE"),
                "candidateCount", 1
            ));
            requestBody.put("generationConfig", generationConfig);
        } else {
            // Imagen Protocol supports separate parameters but NO LONGER handles negativePrompt explicitly
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
                    // Imagen Protocol
                    JsonNode predictions = root.path("predictions");
                    if (predictions.isArray() && predictions.size() > 0) {
                        String base64 = predictions.get(0).path("bytesBase64Encoded").asText();
                        imageBytes = Base64.getDecoder().decode(base64);
                    }
                }

                if (imageBytes != null) {
                    String fileName = "ai_image_" + UUID.randomUUID() + ".png";
                    try (InputStream is = new ByteArrayInputStream(imageBytes)) {
                        String resultUrl = s3Service.uploadFile(fileName, is, userId);
                        logger.info("✅ Image Gen successful: {}", resultUrl);
                        return resultUrl;
                    }
                } else {
                    logger.warn("⚠️ Image Gen response was 200 OK but imageBytes is NULL. Body: {}", root.toString());
                }
            }
            throw new RuntimeException("Image generation failed status: " + response.getStatusCode());
        } catch (HttpStatusCodeException e) {
            String errorBody = e.getResponseBodyAsString();
            logger.error("❌ Image Generation API Error: {} - Body: {}", e.getStatusCode(), errorBody);
            throw new RuntimeException("AI API Error: " + e.getStatusCode() + " - " + errorBody);
        } catch (Exception e) {
            logger.error("❌ Image Generation Exception: {}", e.getMessage(), e);
            throw e;
        }
    }


    private String buildVisualContext(BusinessProfile bp) {
        StringBuilder sb = new StringBuilder();
        
        // Brand Identity Section
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
        
        // Photography & Visual Style Guide
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
        
        // Technical Constraints
        sb.append("\n### 🛠️ TECHNICAL CONSTRAINTS\n");
        if (bp.getTextOverlay() != null && bp.getTextOverlay().isEnabled()) {
            sb.append("- Text Overlay: REQUIRED (Style: ").append(bp.getTextOverlay().getStyle())
              .append(", Position: ").append(bp.getTextOverlay().getPosition()).append(")\n");
        }
        appendIfPresent(sb, "Logo Placement", bp.getLogoPlacement());
        appendIfPresent(sb, "Aspect Ratio", bp.getAspectRatio());
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

    private String sanitizePrompt(String prompt) {
        if (prompt == null) return "";
        // Remove common "buzzy" technical jargon that confuses image models
        String cleaned = prompt.replaceAll("(?i)\\b(photorealistic|hyperrealistic|4k resolution|8k resolution|masterpiece|trending on artstation|unreal engine|octane render|high definition)\\b", "");
        
        // Remove literal hex codes (#FFFFFF, #abc, etc.)
        cleaned = cleaned.replaceAll("#[a-fA-F0-9]{3,6}", "");
        
        // Collapse multiple spaces
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    private String getColorDescription(java.util.List<String> hexCodes) {
        if (hexCodes == null || hexCodes.isEmpty()) return "Natural colors";
        java.util.List<String> readableColors = new java.util.ArrayList<>();
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
        
        // Simple mapping for common values, fallback to generic if complex
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

    public String generateReviewReply(String businessName, String reviewText, int rating) {
        PromptTemplate pt = new PromptTemplate(REVIEW_REPLY_TEMPLATE);
        Prompt prompt = pt.create(Map.of(
                "businessName", businessName != null ? businessName : "our business",
                "rating", rating,
                "reviewText", reviewText
        ));

        try {
            return chatClient.prompt(prompt).call().content();
        } catch (Exception e) {
            logger.error("AI Review Reply failed: {}", e.getMessage(), e);
            return "Thank you for your feedback! We appreciate your support.";
        }
    }
}
