package com.aiplatform.repository;

import com.aiplatform.model.PricingTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PricingRepository extends JpaRepository<PricingTier, Long> {
    Optional<PricingTier> findByName(String name);
}
