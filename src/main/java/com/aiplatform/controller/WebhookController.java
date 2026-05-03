package com.aiplatform.controller;

import com.aiplatform.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/payments/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final PaymentService paymentService;

    @PostMapping(consumes = "application/json")
    public ResponseEntity<String> handleWebhook(
            @RequestBody byte[] rawPayload,
            @RequestHeader("X-Razorpay-Signature") String signature) {

        // Convert raw bytes to String preserving exact encoding for HMAC verification
        String payload = new String(rawPayload, StandardCharsets.UTF_8);
        log.info("Razorpay Webhook Received. Payload length: {}", payload.length());

        paymentService.processWebhook(payload, signature);

        // Always return 200 OK to Razorpay to prevent retries
        return ResponseEntity.ok("Webhook processed");
    }
}
