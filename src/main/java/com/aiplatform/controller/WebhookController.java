package com.aiplatform.controller;

import com.aiplatform.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Razorpay-Signature") String signature) {
        
        log.info("Razorpay Webhook Received with payload length: {}", payload.length());
        
        // This is a simplified call; in production, you should verify the signature in PaymentService
        paymentService.processWebhook(payload, signature);
        
        // Always return 200 OK to Razorpay to prevent retries
        return ResponseEntity.ok("Webhook processed");
    }
}
