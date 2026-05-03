package com.aiplatform.service;

import com.aiplatform.exception.InsufficientCreditsException;
import com.aiplatform.model.*;
import com.aiplatform.repository.PaymentOrderRepository;
import com.aiplatform.repository.UserRepository;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoCreditService {

    private final UserRepository userRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final RazorpayClient razorpayClient; // injected shared bean

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    // ── Get available packs ──────────────────────────────────────────────────

    /**
     * Returns all available packs for a given model, grouped by type.
     */
    public List<Map<String, Object>> getAvailablePacks(String modelId) {
        return Arrays.stream(VideoCreditPack.values())
                .filter(p -> modelId == null || p.getModelId().equalsIgnoreCase(modelId))
                .map(p -> Map.<String, Object>of(
                    "packName",      p.name(),
                    "modelId",       p.getModelId(),
                    "videoCount",    p.getVideoCount(),
                    "priceInr",      p.getPriceInr(),
                    "displayName",   p.getDisplayName(),
                    "packType",      p.getPackType(),
                    "pricePerVideo", String.format("₹%.1f", p.pricePerVideo())
                ))
                .collect(Collectors.toList());
    }

    /**
     * Returns all packs grouped by model.
     */
    public Map<String, Object> getAllPacksGrouped() {
        return Map.of(
            "lite",     getAvailablePacks("veo-lite"),
            "fast",     getAvailablePacks("veo-fast"),
            "standard", getAvailablePacks("veo-standard")
        );
    }

    // ── Custom quantity order ────────────────────────────────────────────────

    /**
     * Per-video price for custom orders (INR).
     * Lite: ₹55, Fast: ₹140, Standard: ₹470
     */
    public static long getCustomPricePerVideo(String modelId) {
        return switch (modelId.toLowerCase()) {
            case "veo-lite"     -> 55L;
            case "veo-fast"     -> 140L;
            case "veo-standard" -> 470L;
            default -> throw new IllegalArgumentException("Unknown model: " + modelId);
        };
    }

    @Transactional
    public PaymentOrder createCustomVideoCreditOrder(User user, String modelId, int quantity) throws RazorpayException {
        long pricePerVideo = getCustomPricePerVideo(modelId);
        long totalInr = pricePerVideo * quantity;

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", totalInr * 100L); // paise
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", "vcustom_" + user.getId() + "_" + System.currentTimeMillis());

        com.razorpay.Order razorpayOrder = razorpayClient.orders.create(orderRequest);

        PaymentOrder paymentOrder = PaymentOrder.builder()
                .razorpayOrderId(razorpayOrder.get("id"))
                .user(user)
                .targetTier(null)
                .amount(totalInr * 100L)
                .currency("INR")
                .orderType("VIDEO_CREDIT_CUSTOM")
                .videoModelId(modelId)
                .videoCreditsPurchased(quantity)
                .status("CREATED")
                .createdAt(LocalDateTime.now())
                .build();

        return paymentOrderRepository.save(paymentOrder);
    }

    // ── Create Razorpay order (pack) ─────────────────────────────────────────

    @Transactional
    public PaymentOrder createVideoCreditOrder(User user, String packName) throws RazorpayException {
        VideoCreditPack pack = VideoCreditPack.fromName(packName);

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", pack.getPriceInr() * 100L); // paise
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", "vcredit_" + user.getId() + "_" + System.currentTimeMillis());

        com.razorpay.Order razorpayOrder = razorpayClient.orders.create(orderRequest);

        PaymentOrder paymentOrder = PaymentOrder.builder()
                .razorpayOrderId(razorpayOrder.get("id"))
                .user(user)
                .targetTier(null)
                .amount(pack.getPriceInr() * 100L)
                .currency("INR")
                .orderType("VIDEO_CREDIT_" + pack.getPackType().toUpperCase()) // VIDEO_CREDIT_PACK or VIDEO_CREDIT_MANUAL
                .videoModelId(pack.getModelId())
                .videoCreditsPurchased(pack.getVideoCount())
                .status("CREATED")
                .createdAt(LocalDateTime.now())
                .build();

        return paymentOrderRepository.save(paymentOrder);
    }

    // ── Verify payment & add credits ─────────────────────────────────────────

    @Transactional
    public Map<String, Object> verifyAndAddCredits(String razorpayOrderId, String razorpayPaymentId,
                                                    String razorpaySignature) throws RazorpayException {
        // 1. Verify signature
        JSONObject attributes = new JSONObject();
        attributes.put("razorpay_order_id", razorpayOrderId);
        attributes.put("razorpay_payment_id", razorpayPaymentId);
        attributes.put("razorpay_signature", razorpaySignature);

        boolean isValid = Utils.verifyPaymentSignature(attributes, razorpayKeySecret);
        if (!isValid) throw new RuntimeException("Invalid payment signature");

        // 2. Find order
        PaymentOrder order = paymentOrderRepository.findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + razorpayOrderId));

        if ("COMPLETED".equals(order.getStatus())) {
            return Map.of("status", "already_processed", "message", "Credits already added");
        }

        // 3. Mark completed
        order.setRazorpayPaymentId(razorpayPaymentId);
        order.setRazorpaySignature(razorpaySignature);
        order.setStatus("COMPLETED");
        order.setCompletedAt(LocalDateTime.now());
        paymentOrderRepository.save(order);

        // 4. Add credits to user wallet
        User user = order.getUser();
        addCreditsToWallet(user, order.getVideoModelId(), order.getVideoCreditsPurchased());

        log.info("Video credits added: {} x {} for user {}", order.getVideoCreditsPurchased(),
                order.getVideoModelId(), user.getEmail());

        return Map.of(
            "status",           "success",
            "message",          order.getVideoCreditsPurchased() + " video credits added to your wallet",
            "modelId",          order.getVideoModelId(),
            "creditsAdded",     order.getVideoCreditsPurchased(),
            "newBalance",       getWalletBalance(user)
        );
    }

    // ── Deduct credit on video generation ───────────────────────────────────

    /**
     * Deducts 1 video credit for the given model from the user's wallet.
     * Throws InsufficientCreditsException if wallet is empty.
     */
    @Transactional
    public void deductVideoCredit(Long userId, VeoModelSelection model) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        switch (model) {
            case VEO_LITE -> {
                int bal = user.getVideoCreditLite() != null ? user.getVideoCreditLite() : 0;
                if (bal <= 0) throw new InsufficientCreditsException(
                    "No Veo Lite video credits. Please purchase more.");
                user.setVideoCreditLite(bal - 1);
            }
            case VEO_FAST -> {
                int bal = user.getVideoCreditFast() != null ? user.getVideoCreditFast() : 0;
                if (bal <= 0) throw new InsufficientCreditsException(
                    "No Veo Fast video credits. Please purchase more.");
                user.setVideoCreditFast(bal - 1);
            }
            case VEO_STANDARD -> {
                int bal = user.getVideoCreditStandard() != null ? user.getVideoCreditStandard() : 0;
                if (bal <= 0) throw new InsufficientCreditsException(
                    "No Veo Standard video credits. Please purchase more.");
                user.setVideoCreditStandard(bal - 1);
            }
        }

        userRepository.save(user);
        log.info("Deducted 1 {} credit for user {}. Remaining: {}",
                model.getModelId(), userId, getBalanceForModel(user, model));
    }

    /**
     * Refunds 1 video credit if generation failed.
     */
    @Transactional
    public void refundVideoCredit(Long userId, VeoModelSelection model) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        switch (model) {
            case VEO_LITE     -> user.setVideoCreditLite((user.getVideoCreditLite() != null ? user.getVideoCreditLite() : 0) + 1);
            case VEO_FAST     -> user.setVideoCreditFast((user.getVideoCreditFast() != null ? user.getVideoCreditFast() : 0) + 1);
            case VEO_STANDARD -> user.setVideoCreditStandard((user.getVideoCreditStandard() != null ? user.getVideoCreditStandard() : 0) + 1);
        }

        userRepository.save(user);
        log.info("Refunded 1 {} credit for user {}", model.getModelId(), userId);
    }

    // ── Wallet balance ───────────────────────────────────────────────────────

    public Map<String, Object> getWalletBalance(User user) {
        int lite     = user.getVideoCreditLite()     != null ? user.getVideoCreditLite()     : 0;
        int fast     = user.getVideoCreditFast()     != null ? user.getVideoCreditFast()     : 0;
        int standard = user.getVideoCreditStandard() != null ? user.getVideoCreditStandard() : 0;

        return Map.of(
            "lite",     lite,
            "fast",     fast,
            "standard", standard,
            "warnings", buildWarnings(lite, fast, standard)
        );
    }

    public Map<String, Object> getWalletBalanceByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return getWalletBalance(user);
    }

    /**
     * Returns true if the user has at least 1 credit for the given model.
     */
    public boolean hasCreditForModel(User user, VeoModelSelection model) {
        return getBalanceForModel(user, model) > 0;
    }

    public int getBalanceForModel(User user, VeoModelSelection model) {
        return switch (model) {
            case VEO_LITE     -> user.getVideoCreditLite()     != null ? user.getVideoCreditLite()     : 0;
            case VEO_FAST     -> user.getVideoCreditFast()     != null ? user.getVideoCreditFast()     : 0;
            case VEO_STANDARD -> user.getVideoCreditStandard() != null ? user.getVideoCreditStandard() : 0;
        };
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void addCreditsToWallet(User user, String modelId, int count) {
        switch (modelId.toLowerCase()) {
            case "veo-lite"     -> user.setVideoCreditLite((user.getVideoCreditLite() != null ? user.getVideoCreditLite() : 0) + count);
            case "veo-fast"     -> user.setVideoCreditFast((user.getVideoCreditFast() != null ? user.getVideoCreditFast() : 0) + count);
            case "veo-standard" -> user.setVideoCreditStandard((user.getVideoCreditStandard() != null ? user.getVideoCreditStandard() : 0) + count);
            default -> throw new IllegalArgumentException("Unknown model: " + modelId);
        }
        userRepository.save(user);
    }

    /**
     * Builds warning messages when Veo Lite credits are low (≤ 2).
     * Only Lite triggers a warning — Fast and Standard do not.
     */
    private List<String> buildWarnings(int lite, int fast, int standard) {
        List<String> warnings = new ArrayList<>();
        if (lite > 0 && lite <= 2)
            warnings.add("⚠️ Only " + lite + " Veo Lite video credit(s) left! Buy more to keep creating.");
        return warnings;
    }
}
