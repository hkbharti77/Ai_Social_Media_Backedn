package com.aiplatform.repository;

import com.aiplatform.model.User;
import com.aiplatform.model.SubscriptionTier;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Boolean existsByEmail(String email);
    List<User> findAllBySubscriptionTierNotAndSubscriptionExpiresAtBefore(SubscriptionTier tier, LocalDateTime now);
}
