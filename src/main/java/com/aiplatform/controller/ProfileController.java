package com.aiplatform.controller;

import com.aiplatform.model.BusinessProfile;
import com.aiplatform.model.User;
import com.aiplatform.repository.BusinessProfileRepository;
import com.aiplatform.repository.UserRepository;
import com.aiplatform.security.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    @Autowired
    private BusinessProfileRepository businessProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public ResponseEntity<BusinessProfile> getProfile() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepository.findById(userDetails.getId()).get();
        return ResponseEntity.ok(businessProfileRepository.findByUser(user)
                .orElse(new BusinessProfile()));
    }

    @PutMapping
    public ResponseEntity<BusinessProfile> updateProfile(@RequestBody BusinessProfile profile) {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepository.findById(userDetails.getId()).get();
        
        BusinessProfile existing = businessProfileRepository.findByUser(user)
                .orElse(BusinessProfile.builder().user(user).build());
        
        existing.setBusinessName(profile.getBusinessName());
        existing.setNiche(profile.getNiche());
        existing.setTargetAudience(profile.getTargetAudience());
        existing.setBrandTone(profile.getBrandTone());
        existing.setPostingFrequency(profile.getPostingFrequency());
        existing.setPreferredHashtags(profile.getPreferredHashtags());

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

        return ResponseEntity.ok(businessProfileRepository.save(existing));
    }
}
