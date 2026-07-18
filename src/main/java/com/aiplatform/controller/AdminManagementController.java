package com.aiplatform.controller;

import com.aiplatform.dto.AdminManagementDtos.*;
import com.aiplatform.model.CreditUsage;
import com.aiplatform.model.PaymentOrder;
import com.aiplatform.model.PostStatus;
import com.aiplatform.model.PricingTier;
import com.aiplatform.service.AdminManagementService;
import com.aiplatform.service.OwnerSecurityService;
import com.aiplatform.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminManagementController {

    private final AdminManagementService adminManagementService;
    private final OwnerSecurityService ownerSecurityService;

    private void validateOwner() {
        com.aiplatform.model.User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        if (!ownerSecurityService.isOwner(user.getEmail())) {
            throw new org.springframework.security.access.AccessDeniedException("Strict Owner Access Only");
        }
    }

    // ==================== User Lifecycle Management ====================

    @PostMapping("/users/{userId}/ban")
    public ResponseEntity<UserActionResponse> banUser(@PathVariable Long userId) {
        validateOwner();
        return ResponseEntity.ok(adminManagementService.banUser(userId));
    }

    @PostMapping("/users/{userId}/unban")
    public ResponseEntity<UserActionResponse> unbanUser(@PathVariable Long userId) {
        validateOwner();
        return ResponseEntity.ok(adminManagementService.unbanUser(userId));
    }

    @PutMapping("/users/{userId}/subscription")
    public ResponseEntity<UserActionResponse> changeSubscription(
            @PathVariable Long userId,
            @Valid @RequestBody SubscriptionChangeRequest request) {
        validateOwner();
        return ResponseEntity.ok(adminManagementService.changeSubscriptionTier(userId, request.getTier()));
    }

    @PostMapping("/users/{userId}/credits/add")
    public ResponseEntity<UserActionResponse> addCredits(
            @PathVariable Long userId,
            @Valid @RequestBody CreditAdjustmentRequest request) {
        validateOwner();
        return ResponseEntity.ok(adminManagementService.addCredits(userId, request));
    }

    @PostMapping("/users/{userId}/credits/deduct")
    public ResponseEntity<UserActionResponse> deductCredits(
            @PathVariable Long userId,
            @Valid @RequestBody CreditAdjustmentRequest request) {
        validateOwner();
        return ResponseEntity.ok(adminManagementService.deductCredits(userId, request));
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long userId) {
        validateOwner();
        adminManagementService.deleteUser(userId);
        return ResponseEntity.ok(Map.of("message", "User " + userId + " permanently deleted"));
    }

    @DeleteMapping("/users/bulk")
    public ResponseEntity<Map<String, Object>> bulkDeleteUsers(@RequestBody List<Long> userIds) {
        validateOwner();
        
        if (userIds == null || userIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "No user IDs provided"));
        }
        
        int successCount = adminManagementService.bulkDeleteUsers(userIds);
        
        return ResponseEntity.ok(Map.of(
            "message", "Bulk delete completed",
            "totalRequested", userIds.size(),
            "successfullyDeleted", successCount
        ));
    }

    @PostMapping("/users/{userId}/reset-password")
    public ResponseEntity<Map<String, String>> resetUserPassword(@PathVariable Long userId) {
        validateOwner();
        adminManagementService.resetUserPassword(userId);
        return ResponseEntity.ok(Map.of("message", "User password has been reset and emailed."));
    }

    @GetMapping("/users/{userId}/profile")
    public ResponseEntity<AdminUserProfileResponse> getUserProfile(@PathVariable Long userId) {
        validateOwner();
        return ResponseEntity.ok(adminManagementService.getUserProfile(userId));
    }

    @GetMapping("/users/summaries")
    public ResponseEntity<List<UserSummary>> getAllUsersSummaries() {
        validateOwner();
        return ResponseEntity.ok(adminManagementService.getAllUsersSummaries());
    }

    // ==================== Fraud and Security ====================

    @GetMapping("/users/fraud-flagged")
    public ResponseEntity<Page<FraudFlaggedUserDto>> getFraudFlaggedUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validateOwner();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(adminManagementService.getFraudFlaggedUsers(pageable));
    }

    @PostMapping("/users/{userId}/fraud-flag")
    public ResponseEntity<FraudFlagResponse> setFraudFlag(@PathVariable Long userId) {
        validateOwner();
        return ResponseEntity.ok(adminManagementService.setFraudFlag(userId));
    }

    @DeleteMapping("/users/{userId}/fraud-flag")
    public ResponseEntity<FraudFlagResponse> clearFraudFlag(@PathVariable Long userId) {
        validateOwner();
        return ResponseEntity.ok(adminManagementService.clearFraudFlag(userId));
    }

    @GetMapping("/users/suspicious-registrations")
    public ResponseEntity<SuspiciousRegistrationsResponse> getSuspiciousRegistrations() {
        validateOwner();
        return ResponseEntity.ok(adminManagementService.getSuspiciousRegistrations());
    }

    // ==================== Payment and Revenue ====================

    @GetMapping("/payments")
    public ResponseEntity<Page<PaymentOrder>> getAllPayments(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validateOwner();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(adminManagementService.getAllPayments(status, pageable));
    }

    @GetMapping("/payments/revenue-stats")
    public ResponseEntity<RevenueStatsResponse> getRevenueStats() {
        validateOwner();
        return ResponseEntity.ok(adminManagementService.getRevenueStats());
    }

    @GetMapping("/users/{userId}/payments")
    public ResponseEntity<List<PaymentOrder>> getUserPayments(@PathVariable Long userId) {
        validateOwner();
        return ResponseEntity.ok(adminManagementService.getUserPayments(userId));
    }

    // ==================== Credit Usage ====================

    @GetMapping("/credits/usage")
    public ResponseEntity<Page<CreditUsage>> getCreditUsageHistory(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validateOwner();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(adminManagementService.getCreditUsageHistory(userId, pageable));
    }

    @GetMapping("/credits/stats")
    public ResponseEntity<CreditStatsResponse> getCreditStats() {
        validateOwner();
        return ResponseEntity.ok(adminManagementService.getCreditStats());
    }

    // ==================== Content Moderation ====================

    @GetMapping("/posts")
    public ResponseEntity<Page<AdminPostDto>> getAllPosts(
            @RequestParam(required = false) PostStatus status,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validateOwner();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(adminManagementService.getAllPosts(status, userId, pageable));
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Map<String, String>> deletePost(@PathVariable Long postId) {
        validateOwner();
        adminManagementService.deletePost(postId);
        return ResponseEntity.ok(Map.of("message", "Post " + postId + " deleted"));
    }

    @GetMapping("/posts/stats")
    public ResponseEntity<PostStatsResponse> getPostStats() {
        validateOwner();
        return ResponseEntity.ok(adminManagementService.getPostStats());
    }

    // ==================== Pricing Administration ====================

    @GetMapping("/pricing")
    public ResponseEntity<List<PricingTier>> getAllPricingTiers() {
        validateOwner();
        return ResponseEntity.ok(adminManagementService.getAllPricingTiers());
    }

    @PutMapping("/pricing/{tierId}")
    public ResponseEntity<PricingTier> updatePricingTier(
            @PathVariable Long tierId,
            @Valid @RequestBody PricingTier tier) {
        validateOwner();
        return ResponseEntity.ok(adminManagementService.updatePricingTier(tierId, tier));
    }

    // ==================== Broadcast and System ====================

    @PostMapping("/broadcast/email")
    public ResponseEntity<BroadcastEmailResponse> broadcastEmail(
            @Valid @RequestBody BroadcastEmailRequest request) {
        validateOwner();
        return ResponseEntity.ok(adminManagementService.broadcastEmail(request));
    }

    @GetMapping("/system/stats")
    public ResponseEntity<SystemStatsResponse> getSystemStats() {
        validateOwner();
        return ResponseEntity.ok(adminManagementService.getSystemStats());
    }
}
