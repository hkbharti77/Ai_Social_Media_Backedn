package com.aiplatform.service;

import com.aiplatform.model.PaymentOrder;
import com.aiplatform.model.SubscriptionTier;
import com.aiplatform.model.User;
import com.aiplatform.repository.PaymentOrderRepository;
import com.aiplatform.util.EmailTemplateUtils;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import jakarta.annotation.PostConstruct;
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

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    @Value("${razorpay.webhook.secret}")
    private String webhookSecret;

    private RazorpayClient razorpayClient;

    @PostConstruct
    public void init() throws RazorpayException {
        this.razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
    }

    @Transactional
    public PaymentOrder createOrder(User user, SubscriptionTier targetTier, Long amountInInr) throws RazorpayException {
        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amountInInr * 100); // amount in the smallest currency unit (paise)
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", "receipt_user_" + user.getId() + "_" + System.currentTimeMillis());

        Order razorpayOrder = razorpayClient.orders.create(orderRequest);

        PaymentOrder paymentOrder = PaymentOrder.builder()
                .razorpayOrderId(razorpayOrder.get("id"))
                .user(user)
                .targetTier(targetTier)
                .amount(amountInInr * 100)
                .currency("INR")
                .status("CREATED")
                .createdAt(LocalDateTime.now())
                .build();

        return paymentOrderRepository.save(paymentOrder);
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
            String subject = "\u2728 Your VaniAI Upgrade is Complete!";
            String htmlBody = EmailTemplateUtils.getPaymentReceiptHtml(order);
            emailService.sendHtmlMessage(order.getUser().getEmail(), subject, htmlBody);
        } catch (Exception e) {
            log.error("\u274C [PaymentService] Failed to send receipt for order {}: {}", order.getRazorpayOrderId(), e.getMessage());
        }
    }
}
