package com.aiplatform.controller;

import com.aiplatform.model.SubscriptionTier;
import com.aiplatform.model.User;
import com.aiplatform.service.PaymentService;
import com.aiplatform.service.SubscriptionService;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import com.aiplatform.security.UserDetailsImpl;
import com.aiplatform.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.aiplatform.service.PdfService;
import com.aiplatform.model.PaymentOrder;
import com.aiplatform.repository.PaymentOrderRepository;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PdfService pdfService;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestBody Map<String, Object> request) throws RazorpayException {
        
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found: " + userDetails.getId()));
        
        String tierName = (String) request.get("tier");
        SubscriptionTier targetTier = SubscriptionTier.valueOf(tierName.toUpperCase().replace(" ", "_"));
        Long amount = Long.valueOf(request.get("amount").toString());

        // Handle Free tier (0 amount) directly
        if (amount <= 0) {
            subscriptionService.upgradePlan(user.getId(), targetTier);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Plan upgraded to Free successfully",
                "is_free", true
            ));
        }

        PaymentOrder paymentOrder = paymentService.createOrder(user, targetTier, amount);
        
        return ResponseEntity.ok(Map.of(
            "order_id", paymentOrder.getRazorpayOrderId(),
            "amount", paymentOrder.getAmount(),
            "currency", "INR",
            "key_id", razorpayKeyId
        ));
    }

    @PostMapping("/verify-payment")
    public ResponseEntity<?> verifyPayment(
            @RequestBody Map<String, String> request) throws RazorpayException {
        
        String orderId = request.get("razorpay_order_id");
        String paymentId = request.get("razorpay_payment_id");
        String signature = request.get("razorpay_signature");

        paymentService.verifyPayment(orderId, paymentId, signature);
        
        return ResponseEntity.ok(Map.of("status", "success", "message", "Payment verified and plan upgraded"));
    }

    @GetMapping("/history")
    public ResponseEntity<List<PaymentOrder>> getPaymentHistory(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        User user = userRepository.findByEmail(userDetails.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        List<PaymentOrder> history = paymentOrderRepository.findByUserOrderByCreatedAtDesc(user);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/receipt/{orderId}")
    public ResponseEntity<byte[]> downloadReceipt(@PathVariable String orderId, @AuthenticationPrincipal UserDetailsImpl userDetails) {
        PaymentOrder order = paymentOrderRepository.findByRazorpayOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        
        // Ensure user only downloads their own receipt
        if (!order.getUser().getEmail().equals(userDetails.getEmail())) {
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
