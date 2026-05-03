package com.aiplatform.controller;

import com.aiplatform.model.PaymentOrder;
import com.aiplatform.model.User;
import com.aiplatform.service.VideoCreditService;
import com.aiplatform.util.SecurityUtils;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints for purchasing and managing video credits.
 *
 * GET  /api/v1/video-credits/packs              → All available packs (grouped by model)
 * GET  /api/v1/video-credits/packs/{modelId}    → Packs for a specific model
 * GET  /api/v1/video-credits/wallet             → User's current video credit balance + warnings
 * POST /api/v1/video-credits/create-order       → Create Razorpay order for a pack
 * POST /api/v1/video-credits/verify-payment     → Verify payment & add credits to wallet
 */
@RestController
@RequestMapping("/api/v1/video-credits")
@RequiredArgsConstructor
public class VideoCreditController {

    private final VideoCreditService videoCreditService;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    // ── Browse packs ─────────────────────────────────────────────────────────

    @GetMapping("/packs")
    public ResponseEntity<?> getAllPacks() {
        return ResponseEntity.ok(videoCreditService.getAllPacksGrouped());
    }

    @GetMapping("/packs/{modelId}")
    public ResponseEntity<?> getPacksByModel(@PathVariable String modelId) {
        return ResponseEntity.ok(videoCreditService.getAvailablePacks(modelId));
    }

    // ── Wallet balance ───────────────────────────────────────────────────────

    @GetMapping("/wallet")
    public ResponseEntity<?> getWallet() {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        return ResponseEntity.ok(videoCreditService.getWalletBalance(user));
    }

    // ── Purchase flow ────────────────────────────────────────────────────────

    /**
     * Step 1: Create a Razorpay order for a video credit pack.
     *
     * Request body: { "packName": "LITE_PACK_10" }
     * Response:     { "order_id": "...", "amount": 44900, "currency": "INR", "key_id": "..." }
     */
    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, String> request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

        String packName = request.get("packName");
        if (packName == null || packName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "packName is required"));
        }

        try {
            PaymentOrder order = videoCreditService.createVideoCreditOrder(user, packName);
            return ResponseEntity.ok(Map.of(
                "order_id",  order.getRazorpayOrderId(),
                "amount",    order.getAmount(),       // in paise
                "currency",  "INR",
                "key_id",    razorpayKeyId,
                "packName",  packName,
                "modelId",   order.getVideoModelId(),
                "credits",   order.getVideoCreditsPurchased()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to create order: " + e.getMessage()));
        }
    }

    // ── Custom quantity order ────────────────────────────────────────────────

    /**
     * Create a Razorpay order for a custom number of video credits.
     *
     * Request body: { "modelId": "veo-lite", "quantity": 7 }
     * Price is calculated as: quantity × per-video price for that model
     *   Lite:     ₹55/video
     *   Fast:     ₹140/video
     *   Standard: ₹470/video
     */
    @PostMapping("/create-custom-order")
    public ResponseEntity<?> createCustomOrder(@RequestBody Map<String, Object> request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

        String modelId = (String) request.get("modelId");
        Object qtyObj  = request.get("quantity");

        if (modelId == null || qtyObj == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "modelId and quantity are required"));
        }

        int quantity;
        try {
            quantity = Integer.parseInt(qtyObj.toString());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "quantity must be a number"));
        }

        if (quantity < 1 || quantity > 500) {
            return ResponseEntity.badRequest().body(Map.of("error", "quantity must be between 1 and 500"));
        }

        try {
            PaymentOrder order = videoCreditService.createCustomVideoCreditOrder(user, modelId, quantity);
            return ResponseEntity.ok(Map.of(
                "order_id",  order.getRazorpayOrderId(),
                "amount",    order.getAmount(),
                "currency",  "INR",
                "key_id",    razorpayKeyId,
                "modelId",   order.getVideoModelId(),
                "credits",   order.getVideoCreditsPurchased(),
                "priceInr",  order.getAmount() / 100
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to create order: " + e.getMessage()));
        }
    }

    /**
     * Step 2: Verify Razorpay payment and add credits to wallet.
     *
     * Request body: {
     *   "razorpay_order_id": "...",
     *   "razorpay_payment_id": "...",
     *   "razorpay_signature": "..."
     * }
     */
    @PostMapping("/verify-payment")
    public ResponseEntity<?> verifyPayment(@RequestBody Map<String, String> request) {
        String orderId    = request.get("razorpay_order_id");
        String paymentId  = request.get("razorpay_payment_id");
        String signature  = request.get("razorpay_signature");

        if (orderId == null || paymentId == null || signature == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing payment verification fields"));
        }

        try {
            Map<String, Object> result = videoCreditService.verifyAndAddCredits(orderId, paymentId, signature);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Verification failed: " + e.getMessage()));
        }
    }
}
