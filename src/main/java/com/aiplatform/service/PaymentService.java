package com.aiplatform.service;

import com.aiplatform.model.PaymentOrder;
import com.aiplatform.model.SubscriptionTier;
import com.aiplatform.model.User;
import com.aiplatform.repository.PaymentOrderRepository;
import com.aiplatform.util.EmailTemplateUtils;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final SubscriptionService subscriptionService;
    private final EmailService emailService;
    private final EmailTemplateUtils emailTemplateUtils;
    private final RazorpayClient razorpayClient; // injected shared bean

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    @Value("${razorpay.webhook.secret}")
    private String webhookSecret;

    @Transactional
    public PaymentOrder createOrder(User user, SubscriptionTier targetTier) throws RazorpayException {
        long finalAmountInInr = subscriptionService.calculateUpgradePrice(user, targetTier);
        
        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", finalAmountInInr * 100);
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", "receipt_user_" + user.getId() + "_" + System.currentTimeMillis());

        com.razorpay.Order razorpayOrder = razorpayClient.orders.create(orderRequest);

        PaymentOrder paymentOrder = PaymentOrder.builder()
                .razorpayOrderId(razorpayOrder.get("id"))
                .user(user)
                .targetTier(targetTier)
                .amount(finalAmountInInr * 100)
                .currency("INR")
                .status("CREATED")
                .createdAt(LocalDateTime.now())
                .build();

        return paymentOrderRepository.save(paymentOrder);
    }

    public long calculatePreviewPrice(User user, SubscriptionTier targetTier) {
        return subscriptionService.calculateUpgradePrice(user, targetTier);
    }

    @Transactional
    public void verifyPayment(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) throws RazorpayException {
        // 1. Verify Signature
        JSONObject attributes = new JSONObject();
        attributes.put("razorpay_order_id", razorpayOrderId);
        attributes.put("razorpay_payment_id", razorpayPaymentId);
        attributes.put("razorpay_signature", razorpaySignature);

        boolean isValid = Utils.verifyPaymentSignature(attributes, razorpayKeySecret);

        if (!isValid) {
            throw new RuntimeException("Invalid payment signature");
        }

        // 2. Update Payment Order
        PaymentOrder paymentOrder = paymentOrderRepository.findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + razorpayOrderId));

        paymentOrder.setRazorpayPaymentId(razorpayPaymentId);
        paymentOrder.setRazorpaySignature(razorpaySignature);
        paymentOrder.setStatus("COMPLETED");
        paymentOrder.setCompletedAt(LocalDateTime.now());
        paymentOrderRepository.save(paymentOrder);

        // 3. Upgrade User Plan
        subscriptionService.upgradePlan(paymentOrder.getUser().getId(), paymentOrder.getTargetTier());
        log.info("User {} upgraded to {} successfully via manual verification", paymentOrder.getUser().getEmail(), paymentOrder.getTargetTier());

        // 4. Send Email Receipt
        sendPaymentReceipt(paymentOrder);
    }

    @Transactional
    public void processWebhook(String payload, String signature) {
        log.info("Processing Razorpay Webhook...");
        
        try {
            // 1. Verify Webhook Signature
            boolean isValid = Utils.verifyWebhookSignature(payload, signature, webhookSecret);
            if (!isValid) {
                log.error("Invalid Webhook Signature!");
                return;
            }

            JSONObject data = new JSONObject(payload);
            String event = data.getString("event");
            
            if ("payment.captured".equals(event)) {
                JSONObject paymentObject = data.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity");
                String orderId = paymentObject.getString("order_id");
                String paymentId = paymentObject.getString("id");

                PaymentOrder paymentOrder = paymentOrderRepository.findByRazorpayOrderId(orderId).orElse(null);
                
                if (paymentOrder != null && !"COMPLETED".equals(paymentOrder.getStatus())) {
                    paymentOrder.setRazorpayPaymentId(paymentId);
                    paymentOrder.setRazorpaySignature(signature); // store webhook signature
                    paymentOrder.setStatus("COMPLETED");
                    paymentOrder.setCompletedAt(LocalDateTime.now());
                    paymentOrderRepository.save(paymentOrder);

                    subscriptionService.upgradePlan(paymentOrder.getUser().getId(), paymentOrder.getTargetTier());
                    log.info("User {} upgraded to {} via Webhook", paymentOrder.getUser().getEmail(), paymentOrder.getTargetTier());

                    // Send Email Receipt
                    sendPaymentReceipt(paymentOrder);
                }
            }
        } catch (Exception e) {
            log.error("Error processing Razorpay webhook", e);
        }
    }

    private void sendPaymentReceipt(PaymentOrder order) {
        try {
            emailService.sendPaymentReceiptEmail(order);
            log.info("✅ [PaymentService] Payment receipt email sent to {} for order {} (Plan: {}, Amount: ₹{})", 
                order.getUser().getEmail(), 
                order.getRazorpayOrderId(), 
                order.getTargetTier(),
                order.getAmount() / 100.0);

            // Admin alert for large payments (≥ ₹500)
            double amountInr = order.getAmount() / 100.0;
            if (amountInr >= 500) {
                try {
                    emailService.sendAdminLargePaymentAlert(
                        order.getUser(), order.getRazorpayOrderId(), amountInr);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.error("\u274C [PaymentService] Failed to send receipt for order {}: {}", order.getRazorpayOrderId(), e.getMessage());
        }
    }

}
