package com.aiplatform.controller;

import com.aiplatform.model.SubscriptionTier;
import com.aiplatform.model.User;
import com.aiplatform.service.PaymentService;
import com.aiplatform.service.SubscriptionService;
import com.aiplatform.util.SecurityUtils;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.aiplatform.service.PdfService;
import com.aiplatform.model.PaymentOrder;
import com.aiplatform.repository.PaymentOrderRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PdfService pdfService;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @PostMapping("/create-order")
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody Map<String, Object> request) throws RazorpayException {
        
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        String tierName = (String) request.get("tier");
        SubscriptionTier targetTier = SubscriptionTier.valueOf(tierName.toUpperCase().replace(" ", "_"));
        
        // Use the new pro-rated service logic
        PaymentOrder paymentOrder = paymentService.createOrder(user, targetTier);
        
        // Handle cases where pro-rating results in 1 INR (essentially free or already covered)
        if (paymentOrder.getAmount() <= 100) { // 100 paise = 1 INR
            // If the adjustment makes it nearly free, we could potentially just upgrade them, 
            // but for tracking, we still create a 1 INR order or handle as free.
            // Let's stick to the order flow if amount > 0.
        }

        return ResponseEntity.ok(Map.of(
            "order_id", paymentOrder.getRazorpayOrderId(),
            "amount", paymentOrder.getAmount(),
            "currency", "INR",
            "key_id", razorpayKeyId
        ));
    }

    @GetMapping("/preview-upgrade/{tier}")
    public ResponseEntity<Map<String, Object>> previewUpgrade(@PathVariable String tier) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        SubscriptionTier targetTier = SubscriptionTier.valueOf(tier.toUpperCase().replace(" ", "_"));
        long proRatedPrice = paymentService.calculatePreviewPrice(user, targetTier);
        double originalPrice = targetTier.getPriceInInr();
        double discount = originalPrice - proRatedPrice;

        return ResponseEntity.ok(Map.of(
            "targetTier", targetTier.name(),
            "originalPrice", originalPrice,
            "proRatedPrice", proRatedPrice,
            "discountApplied", discount,
            "currency", "INR"
        ));
    }

    @PostMapping("/verify-payment")
    public ResponseEntity<Map<String, String>> verifyPayment(@RequestBody Map<String, String> request) throws RazorpayException {
        
        String orderId = request.get("razorpay_order_id");
        String paymentId = request.get("razorpay_payment_id");
        String signature = request.get("razorpay_signature");

        paymentService.verifyPayment(orderId, paymentId, signature);
        
        return ResponseEntity.ok(Map.of("status", "success", "message", "Payment verified and plan upgraded"));
    }

    @GetMapping("/history")
    public ResponseEntity<List<PaymentOrder>> getPaymentHistory() {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        
        List<PaymentOrder> history = paymentOrderRepository.findByUserOrderByCreatedAtDesc(user);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/receipt/{orderId}")
    public ResponseEntity<byte[]> downloadReceipt(@PathVariable String orderId) {
        Long userId = SecurityUtils.getCurrentUserId();
        PaymentOrder order = paymentOrderRepository.findByRazorpayOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        
        // Ensure user only downloads their own receipt
        if (!order.getUser().getId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }

        byte[] pdfContent = pdfService.generatePaymentReceipt(order);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "receipt-" + orderId + ".pdf");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfContent);
    }
}
