package com.aiplatform.controller;

import com.aiplatform.model.BusinessProfile;
import com.aiplatform.model.BrandVoiceMode;
import com.aiplatform.model.User;
import com.aiplatform.repository.BusinessProfileRepository;
import com.aiplatform.repository.UserRepository;
import com.aiplatform.service.AiBestTimeService;
import com.aiplatform.service.AiContentService;
import com.aiplatform.security.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    @Autowired
    private BusinessProfileRepository businessProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AiBestTimeService aiBestTimeService;

    @Autowired
    private AiContentService aiContentService;

    @GetMapping("/all")
    public ResponseEntity<java.util.List<BusinessProfile>> getAllProfiles() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepository.findById(userDetails.getId()).get();
        return ResponseEntity.ok(businessProfileRepository.findAllByUser(user));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepository.findById(userDetails.getId()).get();

        // Auto-generate referral code for legacy users
        if (user.getReferralCode() == null || user.getReferralCode().isBlank()) {
            user.setReferralCode(generateReferralCode(user.getEmail()));
            userRepository.save(user);
        }

        BusinessProfile bp = businessProfileRepository.findAllByUser(user).stream().findFirst().orElse(new BusinessProfile());
        
        Map<String, Object> response = new HashMap<>();
        response.put("profile", bp);
        Map<String, Object> subMap = new HashMap<>();
        subMap.put("tier", user.getSubscriptionTier() != null ? user.getSubscriptionTier() : "FREE");
        subMap.put("tierOrdinal", user.getSubscriptionTier() != null ? user.getSubscriptionTier().ordinal() : 0);
        subMap.put("monthlyCredits", user.getMonthlyCredits() != null ? user.getMonthlyCredits() : 0.0);
        subMap.put("dailyCreditsUsed", user.getDailyCreditsUsed() != null ? user.getDailyCreditsUsed() : 0.0);
        subMap.put("purchasedModelIds", user.getPurchasedModelIds() != null ? user.getPurchasedModelIds() : new java.util.ArrayList<String>());
        subMap.put("maxProfiles", 10); // TODO: Move to PricingTier
        subMap.put("lastGenerationAt", user.getLastGenerationAt() != null ? user.getLastGenerationAt() : "never");
        subMap.put("expiresAt", user.getSubscriptionExpiresAt());
        subMap.put("storedImagesCount", user.getStoredImagesCount() != null ? user.getStoredImagesCount() : 0);
        subMap.put("maxStoredImages", user.getSubscriptionTier() != null ? user.getSubscriptionTier().getMaxStoredImages() : 10);
        subMap.put("referralCode", user.getReferralCode());
        subMap.put("bonusCredits", user.getBonusCredits() != null ? user.getBonusCredits() : 0.0);
        subMap.put("dailyAdsViewed", user.getDailyAdsViewed() != null ? user.getDailyAdsViewed() : 0);
        
        response.put("subscription", subMap);
        
        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<BusinessProfile> updateProfile(@RequestBody BusinessProfile profile) {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepository.findById(userDetails.getId()).get();
        
        BusinessProfile existing = businessProfileRepository.findAllByUser(user)
                .stream().findFirst().orElse(BusinessProfile.builder().user(user).build());
        
        existing.setBusinessName(profile.getBusinessName());
        existing.setBrandSlug(profile.getBrandSlug());
        existing.setNiche(profile.getNiche());
        existing.setTargetAudience(profile.getTargetAudience());
        existing.setBrandTone(profile.getBrandTone());
        existing.setPostingFrequency(profile.getPostingFrequency());
        existing.setPreferredHashtags(profile.getPreferredHashtags());

        // Update Scheduling Fields
        if (profile.getMorningDraftTime() != null) existing.setMorningDraftTime(profile.getMorningDraftTime());
        if (profile.getEveningDraftTime() != null) existing.setEveningDraftTime(profile.getEveningDraftTime());
        if (profile.getMorningPublishTime() != null) existing.setMorningPublishTime(profile.getMorningPublishTime());
        if (profile.getEveningPublishTime() != null) existing.setEveningPublishTime(profile.getEveningPublishTime());
        if (profile.getUseAiBestTime() != null) existing.setUseAiBestTime(profile.getUseAiBestTime());

        // Update Enterprise Image Controls
        existing.setImageStyle(profile.getImageStyle());
        existing.setPeoplePreference(profile.getPeoplePreference());
        existing.setBrandColors(profile.getBrandColors());
        existing.setBrandMood(profile.getBrandMood());
        existing.setDesignStyle(profile.getDesignStyle());
        existing.setVisualConstraints(profile.getVisualConstraints());
        existing.setImageType(profile.getImageType());
        existing.setCompositionStyle(profile.getCompositionStyle());
        existing.setCameraAngle(profile.getCameraAngle());
        existing.setLightingStyle(profile.getLightingStyle());
        existing.setColorTemperature(profile.getColorTemperature());
        existing.setBackgroundStyle(profile.getBackgroundStyle());
        existing.setSubjectFocus(profile.getSubjectFocus());
        existing.setTextOverlay(profile.getTextOverlay());
        existing.setLogoPlacement(profile.getLogoPlacement());
        existing.setAspectRatio(profile.getAspectRatio());
        existing.setQualityLevel(profile.getQualityLevel());
        existing.setCreativityLevel(profile.getCreativityLevel());
        existing.setReferenceImageUrl(profile.getReferenceImageUrl());
        existing.setNegativePrompt(profile.getNegativePrompt());

        // Update Brand Voice Layers
        if (profile.getBrandVoiceSamples() != null) existing.setBrandVoiceSamples(profile.getBrandVoiceSamples());
        if (profile.getBrandVoiceImageUrls() != null) existing.setBrandVoiceImageUrls(profile.getBrandVoiceImageUrls());
        if (profile.getDefaultVoiceMode() != null) existing.setDefaultVoiceMode(profile.getDefaultVoiceMode());

        return ResponseEntity.ok(businessProfileRepository.save(existing));
    }

    @PostMapping("/generate-dna")
    public ResponseEntity<Map<String, String>> generateStyleDna() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepository.findById(userDetails.getId()).get();
        BusinessProfile bp = businessProfileRepository.findAllByUser(user).stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        if (bp.getBrandVoiceSamples() == null || bp.getBrandVoiceSamples().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No samples found to analyze."));
        }

        // Call AI Service to analyze (Need to inject AiContentService or a dedicated VoiceService)
        // For simplicity, let's assume we add this method to AiContentService
        String dna = aiContentService.analyzeBrandVoice(bp.getBrandVoiceSamples(), user.getId());
        bp.setBrandStyleDna(dna);
        businessProfileRepository.save(bp);

        Map<String, String> result = new HashMap<>();
        result.put("dna", dna);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/suggest-best-time")
    public ResponseEntity<com.aiplatform.dto.SchedulingDtos.SuggestedTimes> suggestBestTime() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepository.findById(userDetails.getId()).get();
        BusinessProfile bp = businessProfileRepository.findAllByUser(user).stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Profile not found"));
        
        return ResponseEntity.ok(aiBestTimeService.suggestTimes(bp));
    }

    private String generateReferralCode(String email) {
        String base = email.split("@")[0].replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        if (base.length() > 10) base = base.substring(0, 10);
        
        String random;
        String fullCode;
        do {
            random = java.util.UUID.randomUUID().toString().substring(0, 4);
            fullCode = base + "_" + random;
        } while (userRepository.existsByReferralCode(fullCode));
        
        return fullCode;
    }
}
