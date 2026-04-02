package com.aiplatform.repository;

import com.aiplatform.model.BusinessProfile;
import com.aiplatform.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, Long> {
    List<BusinessProfile> findAllByUser(User user);
    Optional<BusinessProfile> findByBrandSlug(String brandSlug);
    
    List<BusinessProfile> findByMorningDraftTime(String morningDraftTime);
    List<BusinessProfile> findByEveningDraftTime(String eveningDraftTime);
    List<BusinessProfile> findByMorningPublishTime(String morningPublishTime);
    List<BusinessProfile> findByEveningPublishTime(String eveningPublishTime);
}
