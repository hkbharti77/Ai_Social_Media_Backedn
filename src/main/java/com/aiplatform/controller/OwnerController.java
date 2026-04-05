package com.aiplatform.controller;

import com.aiplatform.service.OwnerSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/owner")
@RequiredArgsConstructor
public class OwnerController {

    private final OwnerSecurityService ownerSecurityService;

    /**
     * This will generate a new alphanumeric ID and send it to the configured owner email.
     */
    @PostMapping("/refresh-access")
    public ResponseEntity<?> refreshOwnerAccess() {
        try {
            String result = ownerSecurityService.rotateOwnerPassword();
            return ResponseEntity.ok(Map.of("message", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
