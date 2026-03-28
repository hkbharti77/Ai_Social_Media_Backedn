package com.aiplatform.service;

import com.aiplatform.dto.ContentGenerationDtos.GeneratedPost;
import com.aiplatform.model.BusinessProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Service
public class AiContentService {
    private static final Logger logger = LoggerFactory.getLogger(AiContentService.class);

    private final ChatClient chatClient;
    
    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private S3Service s3Service;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${spring.ai.google.genai.api-key}")
    private String apiKey;

    @Value("${spring.ai.imagen.api-url}")
    private String apiUrl;

    @Value("${spring.ai.imagen.image-model}")
    private String imageModel;

    public AiContentService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    private static final String CAPTION_TEMPLATE = """
            You are a social media expert. Create a {tone} post for {businessName}, a {niche} brand.
            Target audience: {audience}.
            User instruction: {command}.
            
            Visual Brand Identity & Constraints:
            {visualContext}
            
            Return a JSON object exactly like this structure:
            {jsonStructure}
            
            Return ONLY valid JSON wrapped in curly braces. Caption max 280 chars. 
            Crucial: The 'imageSuggestion' must NOT include technical labels or hex codes. 
            However, it SHOULD include the brand name '{businessName}' if a logo or brand text is mentioned in the visual context.
            """;


    public GeneratedPost generatePost(BusinessProfile bp, String userCmd) {
        String visualContext = buildVisualContext(bp);
        PromptTemplate pt = new PromptTemplate(CAPTION_TEMPLATE);
        Prompt prompt = pt.create(Map.of(
                "businessName", bp.getBusinessName() != null ? bp.getBusinessName() : "our brand",
                "niche", bp.getNiche() != null ? bp.getNiche() : "generic",
                "tone", bp.getBrandTone() != null ? bp.getBrandTone() : "professional",
                "audience", bp.getTargetAudience() != null ? bp.getTargetAudience() : "general",
                "command", userCmd,
                "visualContext", visualContext,
                "jsonStructure", "{\"caption\": \"...\", \"hashtags\": [\"#...\", \"...\"], \"imageSuggestion\": \"Detailed description of a professional photo or graphic...\"}"
        ));

        logger.info("🚀 Generating AI post with visual context:\n{}", visualContext);
        logger.debug("Prompt values - Niche: {}, Tone: {}, Audience: {}", 
                     bp.getNiche(), bp.getBrandTone(), bp.getTargetAudience());

        String content;
        try {
            content = chatClient.prompt(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            logger.error("❌ AI Chat Generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("AI Generation failed. Please check your API key or try again later.", e);
        }

        try {
            // Basic extraction in case LLM adds markdown blocks
            if (content.contains("```json")) {
                content = content.substring(content.indexOf("```json") + 7, content.lastIndexOf("```"));
            } else if (content.contains("```")) {
                content = content.substring(content.indexOf("```") + 3, content.lastIndexOf("```"));
            }
            
            GeneratedPost generatedPost = objectMapper.readValue(content, GeneratedPost.class);
            
            // Production-grade Image Generation using Gemini Imagen 3
            if (generatedPost.getImageSuggestion() != null && !generatedPost.getImageSuggestion().isEmpty()) {
                try {
                    String imageUrl = generateAndUploadImage(generatedPost.getImageSuggestion());
                    generatedPost.setImageUrl(imageUrl);
                } catch (Exception e) {
                    // Log the full error for debugging
                    System.err.println("❌ Failed to generate AI image: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            return generatedPost;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI response: " + content, e);
        }
    }

    private String generateAndUploadImage(String suggestion) throws Exception {
        String url = String.format("%s/models/%s:predict?key=%s", apiUrl, imageModel, apiKey);

        Map<String, Object> requestBody = Map.of(
                "instances", Collections.singletonList(Map.of("prompt", suggestion))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode predictionsNode = root.path("predictions");
            if (predictionsNode.isArray() && predictionsNode.size() > 0) {
                String base64Image = predictionsNode.get(0).path("bytesBase64Encoded").asText();
                byte[] imageBytes = Base64.getDecoder().decode(base64Image);
                
                // Upload to S3
                String fileName = "ai_image_" + UUID.randomUUID() + ".png";
                try (InputStream is = new ByteArrayInputStream(imageBytes)) {
                    return s3Service.uploadFile(fileName, is);
                }
            }
        }
        throw new RuntimeException("Image generation failed with status: " + response.getStatusCode() + " body: " + response.getBody());
    }

    private String buildVisualContext(BusinessProfile bp) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, "Business Name", bp.getBusinessName());
        appendIfPresent(sb, "Image Style", bp.getImageStyle());
        appendIfPresent(sb, "People Preference", bp.getPeoplePreference());
        if (bp.getBrandColors() != null && !bp.getBrandColors().isEmpty()) {
            sb.append("- Brand Color Palette (Use for lighting, background tint, and aesthetic influence, NEVER show as text): ")
              .append(String.join(", ", bp.getBrandColors())).append("\n");
        }
        appendIfPresent(sb, "Brand Mood", bp.getBrandMood());
        appendIfPresent(sb, "Design Style", bp.getDesignStyle());
        appendIfPresent(sb, "Visual Constraints", bp.getVisualConstraints());
        appendIfPresent(sb, "Image Type", bp.getImageType());
        appendIfPresent(sb, "Composition Style", bp.getCompositionStyle());
        appendIfPresent(sb, "Camera Angle", bp.getCameraAngle());
        appendIfPresent(sb, "Lighting Style", bp.getLightingStyle());
        appendIfPresent(sb, "Color Temperature", bp.getColorTemperature());
        appendIfPresent(sb, "Background Style", bp.getBackgroundStyle());
        appendIfPresent(sb, "Subject Focus", bp.getSubjectFocus());
        
        if (bp.getTextOverlay() != null && bp.getTextOverlay().isEnabled()) {
            sb.append("- Text Overlay: Enabled (Style: ").append(bp.getTextOverlay().getStyle())
              .append(", Position: ").append(bp.getTextOverlay().getPosition()).append(")\n");
        }
        
        appendIfPresent(sb, "Logo Placement", bp.getLogoPlacement());
        appendIfPresent(sb, "Aspect Ratio", bp.getAspectRatio());
        appendIfPresent(sb, "Quality Level", bp.getQualityLevel());
        appendIfPresent(sb, "Negative Constraints", bp.getNegativePrompt());

        return sb.length() > 0 ? sb.toString() : "Standard professional brand visuals.";
    }

    private void appendIfPresent(StringBuilder sb, String label, Object value) {
        if (value != null && !value.toString().trim().isEmpty()) {
            sb.append("- ").append(label).append(": ").append(value).append("\n");
        }
    }
}
