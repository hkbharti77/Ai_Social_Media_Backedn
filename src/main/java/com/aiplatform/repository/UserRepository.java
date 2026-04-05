package com.aiplatform.repository;

import com.aiplatform.model.User;
import com.aiplatform.model.SubscriptionTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Boolean existsByEmail(String email);
    List<User> findAllBySubscriptionTierNotAndSubscriptionExpiresAtBefore(SubscriptionTier tier, LocalDateTime now);
    Optional<User> findByVerificationToken(String token);
    Optional<User> findByReferralCode(String referralCode);
    Boolean existsByReferralCode(String referralCode);

    Long countByLastLoginAtAfter(LocalDateTime date);

    @Query("SELECT u FROM User u WHERE " +
           "(:query IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%')))")
    org.springframework.data.domain.Page<User> searchUsers(String query, org.springframework.data.domain.Pageable pageable);
}
