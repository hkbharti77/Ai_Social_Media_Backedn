package com.aiplatform.service;

import com.aiplatform.dto.AdminManagementDtos.*;
import com.aiplatform.model.*;
import com.aiplatform.repository.*;
import com.aiplatform.util.EmailTemplateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminManagementService {

    private final UserRepository userRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final CreditUsageRepository creditUsageRepository;
    private final PostRepository postRepository;
    private final PricingRepository pricingRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final OwnerSecurityService ownerSecurityService;
    private final EmailService emailService;
    private final EmailTemplateUtils emailTemplateUtils;
    private final BusinessProfileRepository businessProfileRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MicrositeLinkRepository micrositeLinkRepository;
    private final CommentRepository commentRepository;
    private final PasswordEncoder passwordEncoder;

    // ==================== Helper Methods ====================

    private void rejectIfOwner(User target, String action) {
        if (ownerSecurityService.isOwner(target.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot " + action + " the owner account");
        }
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found: " + userId));
    }

    private Post getPostOrThrow(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Post not found: " + postId));
    }

    private PricingTier getPricingTierOrThrow(Long tierId) {
        return pricingRepository.findById(tierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Pricing tier not found: " + tierId));
    }

    // ==================== User Lifecycle Management ====================

    @Transactional
    public UserActionResponse banUser(Long userId) {
        User user = getUserOrThrow(userId);
        rejectIfOwner(user, "ban");
        
        user.setIsActive(false);
        user = userRepository.save(user);
        
        log.info("Admin banned user: {}", user.getEmail());
        
        return mapToUserActionResponse(user);
    }

    @Transactional
    public UserActionResponse unbanUser(Long userId) {
        User user = getUserOrThrow(userId);
        
        user.setIsActive(true);
        user = userRepository.save(user);
        
        log.info("Admin unbanned user: {}", user.getEmail());
        
        return mapToUserActionResponse(user);
    }

    @Transactional
    public UserActionResponse changeSubscriptionTier(Long userId, SubscriptionTier tier) {
        User user = getUserOrThrow(userId);
        
        user.setSubscriptionTier(tier);
        
        if (tier == SubscriptionTier.FREE) {
            user.setSubscriptionExpiresAt(null);
        } else {
            user.setSubscriptionExpiresAt(LocalDateTime.now().plusDays(30));
        }
        
        user = userRepository.save(user);
        
        log.info("Admin changed subscription tier for user {} to {}", user.getEmail(), tier);
        
        return mapToUserActionResponse(user);
    }

    @Transactional
    public UserActionResponse addCredits(Long userId, CreditAdjustmentRequest request) {
        User user = getUserOrThrow(userId);
        
        validateCreditAdjustmentRequest(request);
        
        double currentBalance = "MONTHLY".equalsIgnoreCase(request.getCreditType()) 
                ? user.getMonthlyCredits() 
                : user.getBonusCredits();
        
        double newBalance = currentBalance + request.getAmount();
        
        if ("MONTHLY".equalsIgnoreCase(request.getCreditType())) {
            user.setMonthlyCredits(newBalance);
        } else {
            user.setBonusCredits(newBalance);
        }
        
        user = userRepository.save(user);
        
        // Create audit trail
        CreditUsage audit = CreditUsage.builder()
                .user(user)
                .amount(request.getAmount())
                .purpose("ADMIN_ADJUSTMENT: " + request.getReason())
                .build();
        creditUsageRepository.save(audit);
        
        log.info("Admin added {} {} credits to user {}: {}", 
                request.getAmount(), request.getCreditType(), user.getEmail(), request.getReason());
        
        return mapToUserActionResponse(user);
    }

    @Transactional
    public UserActionResponse deductCredits(Long userId, CreditAdjustmentRequest request) {
        User user = getUserOrThrow(userId);
        
        validateCreditAdjustmentRequest(request);
        
        double currentBalance = "MONTHLY".equalsIgnoreCase(request.getCreditType()) 
                ? user.getMonthlyCredits() 
                : user.getBonusCredits();
        
        // Floor at 0.0
        double newBalance = Math.max(0.0, currentBalance - request.getAmount());
        
        if ("MONTHLY".equalsIgnoreCase(request.getCreditType())) {
            user.setMonthlyCredits(newBalance);
        } else {
            user.setBonusCredits(newBalance);
        }
        
        user = userRepository.save(user);
        
        // Create audit trail with negative amount
        CreditUsage audit = CreditUsage.builder()
                .user(user)
                .amount(-request.getAmount())
                .purpose("ADMIN_ADJUSTMENT: " + request.getReason())
                .build();
        creditUsageRepository.save(audit);
        
        log.info("Admin deducted {} {} credits from user {}: {}", 
                request.getAmount(), request.getCreditType(), user.getEmail(), request.getReason());
        
        return mapToUserActionResponse(user);
    }

    private void validateCreditAdjustmentRequest(CreditAdjustmentRequest request) {
        if (request.getAmount() == null || request.getAmount() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Amount must be a positive number");
        }
        
        if (!"MONTHLY".equalsIgnoreCase(request.getCreditType()) && 
            !"BONUS".equalsIgnoreCase(request.getCreditType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Invalid credit type. Must be MONTHLY or BONUS");
        }
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = getUserOrThrow(userId);
        rejectIfOwner(user, "delete");
        
        // Delete all related entities in the correct order to avoid foreign key constraint violations
        
        // 1. Delete business profiles (causing the current error)
        List<BusinessProfile> businessProfiles = businessProfileRepository.findAllByUser(user);
        businessProfileRepository.deleteAll(businessProfiles);
        log.debug("Deleted {} business profiles for user {}", businessProfiles.size(), user.getEmail());
        
        // 2. Delete social accounts
        List<SocialAccount> socialAccounts = socialAccountRepository.findByUser(user);
        socialAccountRepository.deleteAll(socialAccounts);
        log.debug("Deleted {} social accounts for user {}", socialAccounts.size(), user.getEmail());
        
        // 3. Delete refresh tokens
        refreshTokenRepository.deleteByUser(user);
        log.debug("Deleted refresh tokens for user {}", user.getEmail());
        
        // 4. Delete posts
        List<Post> posts = postRepository.findByUser(user);
        postRepository.deleteAll(posts);
        log.debug("Deleted {} posts for user {}", posts.size(), user.getEmail());
        
        // 5. Delete microsite links
        List<MicrositeLink> micrositeLinks = micrositeLinkRepository.findByUserOrderBySortOrderAsc(user);
        micrositeLinkRepository.deleteAll(micrositeLinks);
        log.debug("Deleted {} microsite links for user {}", micrositeLinks.size(), user.getEmail());
        
        // 6. Delete comments
        List<Comment> comments = commentRepository.findByUserOrderByCreatedAtDesc(user);
        commentRepository.deleteAll(comments);
        log.debug("Deleted {} comments for user {}", comments.size(), user.getEmail());
        
        // 7. Delete credit usage records
        List<CreditUsage> creditUsages = creditUsageRepository.findByUserOrderByCreatedAtDesc(user);
        creditUsageRepository.deleteAll(creditUsages);
        log.debug("Deleted {} credit usage records for user {}", creditUsages.size(), user.getEmail());
        
        // 8. Delete AI usage logs
        List<AiUsageLog> aiUsageLogs = aiUsageLogRepository.findByUserOrderByCreatedAtDesc(user);
        aiUsageLogRepository.deleteAll(aiUsageLogs);
        log.debug("Deleted {} AI usage logs for user {}", aiUsageLogs.size(), user.getEmail());
        
        // 9. Payment orders are kept for audit purposes, but we could optionally anonymize them
        // For now, we'll keep them as they're important for financial records
        
        // 10. Finally, delete the user
        userRepository.delete(user);
        
        log.warn("Admin permanently deleted user: {} and all related data", user.getEmail());
    }

    @Transactional
    public int bulkDeleteUsers(List<Long> userIds) {
        int successCount = 0;
        int failedCount = 0;
        List<String> errors = new ArrayList<>();
        
        for (Long userId : userIds) {
            try {
                deleteUser(userId);
                successCount++;
            } catch (Exception e) {
                failedCount++;
                errors.add("User " + userId + ": " + e.getMessage());
                log.error("Failed to delete user {}: {}", userId, e.getMessage());
            }
        }
        
        log.warn("Bulk delete completed: {} successful, {} failed out of {} total", 
                successCount, failedCount, userIds.size());
        
        if (!errors.isEmpty()) {
            log.error("Bulk delete errors: {}", String.join("; ", errors));
        }
        
        return successCount;
    }

    public AdminUserProfileResponse getUserProfile(Long userId) {
        User user = getUserOrThrow(userId);
        
        return AdminUserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .isActive(user.getIsActive())
                .subscriptionTier(user.getSubscriptionTier())
                .subscriptionExpiresAt(user.getSubscriptionExpiresAt())
                .monthlyCredits(user.getMonthlyCredits())
                .bonusCredits(user.getBonusCredits())
                .isFraudFlagged(user.getIsFraudFlagged())
                .registrationIp(user.getRegistrationIp())
                .deviceFingerprint(user.getDeviceFingerprint())
                .loginCount(user.getLoginCount())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .roles(user.getRoles())
                .storedImagesCount(user.getStoredImagesCount())
                .storedVideosCount(user.getStoredVideosCount())
                .totalUsageMinutes(user.getTotalUsageMinutes())
                .build();
    }

    public List<UserSummary> getAllUsersSummaries() {
        return userRepository.findAll().stream()
                .map(user -> UserSummary.builder()
                        .userId(user.getId())
                        .email(user.getEmail())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void resetUserPassword(Long userId) {
        User user = getUserOrThrow(userId);
        rejectIfOwner(user, "reset password for");

        String newPassword = generateRandomPassword();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        log.info("Admin reset password for user: {}", user.getEmail());
        
        emailService.sendManualPasswordResetEmail(user, newPassword);
    }

    private String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // ==================== Fraud and Security ====================

    public Page<FraudFlaggedUserDto> getFraudFlaggedUsers(Pageable pageable) {
        return userRepository.findByIsFraudFlaggedTrue(pageable)
                .map(user -> FraudFlaggedUserDto.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .isActive(user.getIsActive())
                        .subscriptionTier(user.getSubscriptionTier())
                        .registrationIp(user.getRegistrationIp())
                        .isFraudFlagged(user.getIsFraudFlagged())
                        .build());
    }

    @Transactional
    public FraudFlagResponse setFraudFlag(Long userId) {
        User user = getUserOrThrow(userId);
        
        user.setIsFraudFlagged(true);
        user = userRepository.save(user);
        
        log.warn("Admin set fraud flag for user: {}", user.getEmail());
        
        return FraudFlagResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .isFraudFlagged(user.getIsFraudFlagged())
                .build();
    }

    @Transactional
    public FraudFlagResponse clearFraudFlag(Long userId) {
        User user = getUserOrThrow(userId);
        
        user.setIsFraudFlagged(false);
        user = userRepository.save(user);
        
        log.info("Admin cleared fraud flag for user: {}", user.getEmail());
        
        return FraudFlagResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .isFraudFlagged(user.getIsFraudFlagged())
                .build();
    }

    public SuspiciousRegistrationsResponse getSuspiciousRegistrations() {
        // Find duplicate IPs
        List<String> dupIps = userRepository.findDuplicateRegistrationIps();
        List<DuplicateIpGroup> ipGroups = dupIps.stream()
                .map(ip -> {
                    List<User> users = userRepository.findByRegistrationIp(ip);
                    List<UserSummary> summaries = users.stream()
                            .map(u -> UserSummary.builder()
                                    .userId(u.getId())
                                    .email(u.getEmail())
                                    .build())
                            .collect(Collectors.toList());
                    
                    return DuplicateIpGroup.builder()
                            .registrationIp(ip)
                            .users(summaries)
                            .build();
                })
                .collect(Collectors.toList());
        
        // Find duplicate fingerprints
        List<String> dupFingerprints = userRepository.findDuplicateDeviceFingerprints();
        List<DuplicateFingerprintGroup> fingerprintGroups = dupFingerprints.stream()
                .map(fingerprint -> {
                    List<User> users = userRepository.findByDeviceFingerprint(fingerprint);
                    List<UserSummary> summaries = users.stream()
                            .map(u -> UserSummary.builder()
                                    .userId(u.getId())
                                    .email(u.getEmail())
                                    .build())
                            .collect(Collectors.toList());
                    
                    return DuplicateFingerprintGroup.builder()
                            .deviceFingerprint(fingerprint)
                            .users(summaries)
                            .build();
                })
                .collect(Collectors.toList());
        
        return SuspiciousRegistrationsResponse.builder()
                .duplicateIpGroups(ipGroups)
                .duplicateFingerprintGroups(fingerprintGroups)
                .build();
    }

    // ==================== Payment and Revenue ====================

    public Page<PaymentOrder> getAllPayments(String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            return paymentOrderRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        }
        return paymentOrderRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public RevenueStatsResponse getRevenueStats() {
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        
        Long totalRevenue = paymentOrderRepository.sumCompletedRevenue();
        Long monthlyRevenue = paymentOrderRepository.sumCompletedRevenueFrom(startOfMonth);
        Long dailyRevenue = paymentOrderRepository.sumCompletedRevenueFrom(startOfDay);
        
        Long totalCount = paymentOrderRepository.countCompleted();
        Long monthlyCount = paymentOrderRepository.countCompletedFrom(startOfMonth);
        Long dailyCount = paymentOrderRepository.countCompletedFrom(startOfDay);
        
        return RevenueStatsResponse.builder()
                .totalRevenue(totalRevenue != null ? totalRevenue : 0L)
                .monthlyRevenue(monthlyRevenue != null ? monthlyRevenue : 0L)
                .dailyRevenue(dailyRevenue != null ? dailyRevenue : 0L)
                .totalCompletedCount(totalCount != null ? totalCount : 0L)
                .monthlyCompletedCount(monthlyCount != null ? monthlyCount : 0L)
                .dailyCompletedCount(dailyCount != null ? dailyCount : 0L)
                .currency("INR")
                .build();
    }

    public List<PaymentOrder> getUserPayments(Long userId) {
        User user = getUserOrThrow(userId);
        return paymentOrderRepository.findByUserOrderByCreatedAtDesc(user);
    }

    // ==================== Credit Usage ====================

    public Page<CreditUsage> getCreditUsageHistory(Long userId, Pageable pageable) {
        if (userId != null) {
            User user = getUserOrThrow(userId);
            return creditUsageRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        }
        return creditUsageRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public CreditStatsResponse getCreditStats() {
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        
        Double totalCredits = creditUsageRepository.sumAllCredits();
        Double monthlyCredits = creditUsageRepository.sumCreditsFrom(startOfMonth);
        
        // Top 10 users
        List<Map<String, Object>> topUsersRaw = creditUsageRepository.findTopCreditUsers(
                PageRequest.of(0, 10));
        List<TopCreditUser> topUsers = topUsersRaw.stream()
                .map(map -> TopCreditUser.builder()
                        .userId(((Number) map.get("userId")).longValue())
                        .email((String) map.get("email"))
                        .totalCredits(((Number) map.get("totalCredits")).doubleValue())
                        .build())
                .collect(Collectors.toList());
        
        // By purpose
        List<Map<String, Object>> byPurposeRaw = creditUsageRepository.sumCreditsByPurpose();
        Map<String, Double> byPurpose = byPurposeRaw.stream()
                .collect(Collectors.toMap(
                        map -> (String) map.get("purpose"),
                        map -> ((Number) map.get("total")).doubleValue()
                ));
        
        return CreditStatsResponse.builder()
                .totalCredits(totalCredits != null ? totalCredits : 0.0)
                .monthlyCredits(monthlyCredits != null ? monthlyCredits : 0.0)
                .topUsers(topUsers)
                .byPurpose(byPurpose)
                .build();
    }

    // ==================== Content Moderation ====================

    public Page<AdminPostDto> getAllPosts(PostStatus status, Long userId, Pageable pageable) {
        return postRepository.findAllWithFilters(status, userId, pageable)
                .map(post -> AdminPostDto.builder()
                        .id(post.getId())
                        .userId(post.getUser().getId())
                        .userEmail(post.getUser().getEmail())
                        .caption(truncateCaption(post.getCaption()))
                        .status(post.getStatus())
                        .platform(post.getPlatform())
                        .createdAt(post.getCreatedAt())
                        .publishedAt(post.getPublishedAt())
                        .build());
    }

    private String truncateCaption(String caption) {
        if (caption == null) return null;
        return caption.length() > 200 ? caption.substring(0, 200) + "..." : caption;
    }

    @Transactional
    public void deletePost(Long postId) {
        Post post = getPostOrThrow(postId);
        postRepository.delete(post);
        
        log.warn("Admin deleted post: {} by user {}", postId, post.getUser().getEmail());
    }

    public PostStatsResponse getPostStats() {
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        
        Long total = postRepository.count();
        
        Map<String, Long> byStatus = new HashMap<>();
        for (PostStatus status : PostStatus.values()) {
            Long count = postRepository.countByStatus(status);
            byStatus.put(status.name(), count != null ? count : 0L);
        }
        
        Long thisMonth = postRepository.countCreatedFrom(startOfMonth);
        Long publishedToday = postRepository.countPublishedFrom(startOfDay);
        
        return PostStatsResponse.builder()
                .total(total)
                .byStatus(byStatus)
                .thisMonth(thisMonth != null ? thisMonth : 0L)
                .publishedToday(publishedToday != null ? publishedToday : 0L)
                .build();
    }

    // ==================== Pricing Administration ====================

    public List<PricingTier> getAllPricingTiers() {
        return pricingRepository.findAll();
    }

    @Transactional
    public PricingTier updatePricingTier(Long tierId, PricingTier update) {
        PricingTier existing = getPricingTierOrThrow(tierId);
        
        if (update.getPriceInr() != null) existing.setPriceInr(update.getPriceInr());
        if (update.getPriceAmount() != null) existing.setPriceAmount(update.getPriceAmount());
        if (update.getDescription() != null) existing.setDescription(update.getDescription());
        if (update.getMonthlyCredits() != null) existing.setMonthlyCredits(update.getMonthlyCredits());
        if (update.getDailyLimit() != null) existing.setDailyLimit(update.getDailyLimit());
        if (update.getMaxProfiles() != null) existing.setMaxProfiles(update.getMaxProfiles());
        if (update.getPopular() != null) existing.setPopular(update.getPopular());
        if (update.getFeatures() != null) existing.setFeatures(update.getFeatures());
        
        existing = pricingRepository.save(existing);
        
        log.info("Admin updated pricing tier: {}", existing.getName());
        
        return existing;
    }

    // ==================== Broadcast ====================

    @Transactional(readOnly = true)
    public BroadcastEmailResponse broadcastEmail(BroadcastEmailRequest request) {
        validateBroadcastRequest(request);
        
        List<User> recipients = new ArrayList<>();
        
        if ("SPECIFIC".equalsIgnoreCase(request.getTargetTier())) {
            if (request.getTargetEmail() == null || request.getTargetEmail().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target email is required for SPECIFIC tier");
            }
            User user = userRepository.findByEmail(request.getTargetEmail())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + request.getTargetEmail()));
            recipients.add(user);
        } else if ("ALL".equalsIgnoreCase(request.getTargetTier())) {
            recipients = userRepository.findAll();
        } else if (request.getTargetTier() != null) {
            try {
                SubscriptionTier tier = SubscriptionTier.valueOf(request.getTargetTier().toUpperCase());
                recipients = userRepository.findBySubscriptionTier(tier);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        "Invalid target tier: " + request.getTargetTier());
            }
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target tier or target email is required");
        }
        
        log.info("Broadcasting email to {} recipients (tier: {}, specific: {})", 
                recipients.size(), request.getTargetTier(), request.getTargetEmail());
        
        int successCount = 0;
        int failedCount = 0;
        
        // Send emails asynchronously in the background
        for (User user : recipients) {
            // Validate email format before attempting to send
            if (user.getEmail() == null || !isValidEmail(user.getEmail())) {
                log.warn("Skipping user {} with invalid email: {}", user.getId(), user.getEmail());
                failedCount++;
                continue;
            }
            
            try {
                emailService.sendBroadcastEmail(user.getEmail(), request.getSubject(), request.getHtmlBody());
                successCount++;
            } catch (Exception e) {
                log.error("Failed to send broadcast email to {}: {}", user.getEmail(), e.getMessage());
                failedCount++;
            }
        }
        
        log.info("Broadcast email completed: {} successful, {} failed out of {} total", 
                successCount, failedCount, recipients.size());
        
        return BroadcastEmailResponse.builder()
                .recipientCount(successCount)
                .status("DISPATCHED")
                .build();
    }
    
    private boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        // Basic email validation regex
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(emailRegex);
    }

    private void validateBroadcastRequest(BroadcastEmailRequest request) {
        if (request.getSubject() == null || request.getSubject().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subject must not be empty");
        }
        if (request.getHtmlBody() == null || request.getHtmlBody().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email body must not be empty");
        }
        if ((request.getTargetTier() == null || request.getTargetTier().isBlank()) && 
            (request.getTargetEmail() == null || request.getTargetEmail().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Either targetTier or targetEmail must be specified");
        }
    }

    // ==================== System Stats ====================

    public SystemStatsResponse getSystemStats() {
        Long totalUsers = userRepository.count();
        
        Map<String, Long> usersByTier = new HashMap<>();
        for (SubscriptionTier tier : SubscriptionTier.values()) {
            Long count = (long) userRepository.findBySubscriptionTier(tier).size();
            usersByTier.put(tier.name(), count);
        }
        
        Long activeUsers = userRepository.countByIsActiveTrue();
        Long fraudFlaggedUsers = userRepository.countByIsFraudFlaggedTrue();
        
        Long totalRevenue = paymentOrderRepository.sumCompletedRevenue();
        Double totalCreditsConsumed = creditUsageRepository.sumAllCredits();
        
        Long totalPosts = postRepository.count();
        Long totalAiTokens = aiUsageLogRepository.sumTotalTokens();
        
        return SystemStatsResponse.builder()
                .totalUsers(totalUsers)
                .usersByTier(usersByTier)
                .activeUsers(activeUsers != null ? activeUsers : 0L)
                .fraudFlaggedUsers(fraudFlaggedUsers != null ? fraudFlaggedUsers : 0L)
                .totalRevenue(totalRevenue != null ? totalRevenue : 0L)
                .totalCreditsConsumed(totalCreditsConsumed != null ? totalCreditsConsumed : 0.0)
                .totalPosts(totalPosts)
                .totalAiTokens(totalAiTokens != null ? totalAiTokens : 0L)
                .build();
    }

    // ==================== Mapping Helpers ====================

    private UserActionResponse mapToUserActionResponse(User user) {
        return UserActionResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .isActive(user.getIsActive())
                .subscriptionTier(user.getSubscriptionTier())
                .subscriptionExpiresAt(user.getSubscriptionExpiresAt())
                .monthlyCredits(user.getMonthlyCredits())
                .bonusCredits(user.getBonusCredits())
                .build();
    }
}
