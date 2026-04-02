package com.aiplatform.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "business_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "business_name")
    private String businessName;

    @Column(name = "brand_slug", unique = true)
    private String brandSlug;

    private String niche;
    
    @Column(name = "target_audience")
    private String targetAudience;
    
    @Column(name = "brand_tone")
    private String brandTone;
    
    @Column(name = "posting_frequency")
    private Integer postingFrequency;
    
    @Column(name = "preferred_hashtags", length = 1000)
    private String preferredHashtags;

    // --- Enterprise Image Control Layers ---
    
    @Column(name = "image_style")
    private String imageStyle; // e.g., cinematic
    
    @Column(name = "people_preference")
    private String peoplePreference; // e.g., PROFESSIONALS
    
    @ElementCollection
    @CollectionTable(name = "profile_brand_colors", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "color")
    private java.util.List<String> brandColors;
    
    @Column(name = "brand_mood")
    private String brandMood;
    
    @Column(name = "design_style")
    private String designStyle;
    
    @Column(name = "visual_constraints")
    private String visualConstraints;
    
    @Column(name = "image_type")
    private String imageType;
    
    @Column(name = "composition_style")
    private String compositionStyle;
    
    @Column(name = "camera_angle")
    private String cameraAngle;
    
    @Column(name = "lighting_style")
    private String lightingStyle;
    
    @Column(name = "color_temperature")
    private String colorTemperature;
    
    @Column(name = "background_style")
    private String backgroundStyle;
    
    @Column(name = "subject_focus")
    private String subjectFocus;
    
    @Embedded
    private TextOverlay textOverlay;
    
    @Column(name = "logo_placement")
    private String logoPlacement;
    
    @Column(name = "aspect_ratio")
    private String aspectRatio;
    
    @Column(name = "quality_level")
    private String qualityLevel;
    
    @Column(name = "creativity_level")
    private Double creativityLevel;
    
    @Column(name = "reference_image_url")
    private String referenceImageUrl;
    
    @Column(name = "negative_prompt")
    private String negativePrompt;

    // --- Dynamic Scheduling Fields (HH:mm format) ---
    
    @Column(name = "morning_draft_time")
    @Builder.Default
    private String morningDraftTime = "06:00"; 

    @Column(name = "evening_draft_time")
    @Builder.Default
    private String eveningDraftTime = "15:00";

    @Column(name = "morning_publish_time")
    @Builder.Default
    private String morningPublishTime = "09:00";

    @Column(name = "evening_publish_time")
    @Builder.Default
    private String eveningPublishTime = "20:00";

    @Column(name = "use_ai_best_time")
    @Builder.Default
    private Boolean useAiBestTime = false;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TextOverlay {
        private boolean enabled;
        private String style;
        private String position;
    }
}
