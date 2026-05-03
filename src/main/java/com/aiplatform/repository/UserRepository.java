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

    // Admin Management - Fraud management
    org.springframework.data.domain.Page<User> findByIsFraudFlaggedTrue(org.springframework.data.domain.Pageable pageable);
    List<User> findBySubscriptionTier(SubscriptionTier tier);
    Long countByIsActiveTrue();
    Long countByIsFraudFlaggedTrue();

    // Admin Management - Suspicious registration detection
    @Query("SELECT u.registrationIp FROM User u " +
           "WHERE u.registrationIp IS NOT NULL AND u.registrationIp <> '' " +
           "GROUP BY u.registrationIp HAVING COUNT(u) > 1")
    List<String> findDuplicateRegistrationIps();

    @Query("SELECT u.deviceFingerprint FROM User u " +
           "WHERE u.deviceFingerprint IS NOT NULL AND u.deviceFingerprint <> '' " +
           "GROUP BY u.deviceFingerprint HAVING COUNT(u) > 1")
    List<String> findDuplicateDeviceFingerprints();

    List<User> findByRegistrationIp(String registrationIp);
    List<User> findByDeviceFingerprint(String deviceFingerprint);
}
