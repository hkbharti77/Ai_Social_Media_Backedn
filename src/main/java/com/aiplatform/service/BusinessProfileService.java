package com.aiplatform.service;

import com.aiplatform.model.BusinessProfile;
import com.aiplatform.repository.BusinessProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BusinessProfileService {
    private final BusinessProfileRepository businessProfileRepository;

    public BusinessProfile getProfile(Long userId) {
        // Assuming one profile per user for now
        List<BusinessProfile> profiles = businessProfileRepository.findAll();
        return profiles.stream()
                .filter(p -> p.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Business Profile not found for user: " + userId));
    }
}
