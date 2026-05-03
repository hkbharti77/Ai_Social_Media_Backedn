package com.aiplatform.controller;

import com.aiplatform.model.PricingTier;
import com.aiplatform.service.OwnerSecurityService;
import com.aiplatform.service.PricingService;
import com.aiplatform.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingService pricingService;
    private final OwnerSecurityService ownerSecurityService;

    private void validateOwner() {
        com.aiplatform.model.User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        if (!ownerSecurityService.isOwner(user.getEmail())) {
            throw new org.springframework.security.access.AccessDeniedException("Strict Owner Access Only");
        }
    }

    @GetMapping
    public List<PricingTier> getPricingTiers() {
        return pricingService.getAllTiers();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PricingTier updatePricingTier(@PathVariable Long id, @RequestBody PricingTier tier) {
        validateOwner();
        return pricingService.updateTier(id, tier);
    }
}
