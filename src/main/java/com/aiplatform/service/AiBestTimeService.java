package com.aiplatform.service;

import com.aiplatform.dto.SchedulingDtos.SuggestedTimes;
import com.aiplatform.model.BusinessProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


import java.util.HashMap;
import java.util.Map;

@Service
public class AiBestTimeService {

    private static final Logger logger = LoggerFactory.getLogger(AiBestTimeService.class);

    private static final Map<String, ProfileSuggestion> NICHE_SUGGESTIONS = new HashMap<>();

    static {
        // Mock data based on general social media engagement research
        NICHE_SUGGESTIONS.put("real estate", new ProfileSuggestion("08:30", "11:00", "09:30", "18:30"));
        NICHE_SUGGESTIONS.put("fitness", new ProfileSuggestion("06:00", "16:00", "07:00", "20:00"));
        NICHE_SUGGESTIONS.put("saas", new ProfileSuggestion("09:00", "14:00", "10:30", "16:30"));
        NICHE_SUGGESTIONS.put("e-commerce", new ProfileSuggestion("10:00", "17:00", "12:00", "21:00"));
        NICHE_SUGGESTIONS.put("travel", new ProfileSuggestion("07:00", "19:00", "09:00", "22:00"));
        NICHE_SUGGESTIONS.put("default", new ProfileSuggestion("06:00", "15:00", "09:00", "20:00"));
    }

    public SuggestedTimes suggestTimes(BusinessProfile profile) {
        String niche = profile.getNiche() != null ? profile.getNiche().toLowerCase() : "default";
        
        ProfileSuggestion suggestion = NICHE_SUGGESTIONS.entrySet().stream()
                .filter(e -> niche.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(NICHE_SUGGESTIONS.get("default"));

        logger.info("🤖 [AiBestTime] Generating suggestions for niche: {} -> {}", niche, suggestion);

        return SuggestedTimes.builder()
                .morningDraftTime(suggestion.morningDraft)
                .eveningDraftTime(suggestion.eveningDraft)
                .morningPublishTime(suggestion.morningPublish)
                .eveningPublishTime(suggestion.eveningPublish)
                .reason("Based on optimal engagement data for the " + niche + " industry.")
                .build();
    }

    private static class ProfileSuggestion {
        String morningDraft;
        String eveningDraft;
        String morningPublish;
        String eveningPublish;

        ProfileSuggestion(String md, String ed, String mp, String ep) {
            this.morningDraft = md;
            this.eveningDraft = ed;
            this.morningPublish = mp;
            this.eveningPublish = ep;
        }

        @Override
        public String toString() {
            return String.format("Drafts: %s/%s, Publish: %s/%s", morningDraft, eveningDraft, morningPublish, eveningPublish);
        }
    }
}
