package com.aiplatform.service;

import com.aiplatform.model.BusinessProfile;
import com.aiplatform.model.MicrositeLink;
import com.aiplatform.model.User;
import com.aiplatform.repository.BusinessProfileRepository;
import com.aiplatform.repository.MicrositeLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
@RequiredArgsConstructor
public class MicrositeService {
    private final MicrositeLinkRepository linkRepository;
    private final BusinessProfileRepository profileRepository;

    public List<MicrositeLink> getLinksForUser(User user) {
        return linkRepository.findByUserOrderBySortOrderAsc(user);
    }

    public List<MicrositeLink> getPublicLinks(String brandSlug) {
        BusinessProfile profile = profileRepository.findByBrandSlug(brandSlug)
                .orElseThrow(() -> new RuntimeException("Profile not found"));
        return linkRepository.findByUserOrderBySortOrderAsc(profile.getUser());
    }

    public BusinessProfile getProfileBySlug(String brandSlug) {
        return profileRepository.findByBrandSlug(brandSlug)
                .orElseThrow(() -> new RuntimeException("Profile not found"));
    }

    public MicrositeLink addLink(User user, String title, String url) {
        MicrositeLink link = MicrositeLink.builder()
                .user(user)
                .title(title)
                .url(url)
                .build();
        return linkRepository.save(link);
    }

    public void deleteLink(Long linkId, User user) {
        MicrositeLink link = linkRepository.findById(linkId)
                .orElseThrow(() -> new RuntimeException("Link not found"));
        if (!link.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        linkRepository.delete(link);
    }

    @Transactional
    public void trackClick(Long linkId) {
        linkRepository.incrementClickCount(linkId);
    }
}
