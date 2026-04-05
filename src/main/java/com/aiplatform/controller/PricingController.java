package com.aiplatform.controller;

import com.aiplatform.model.PricingTier;
import com.aiplatform.service.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingService pricingService;

    @GetMapping
    public List<PricingTier> getPricingTiers() {
        return pricingService.getAllTiers();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PricingTier updatePricingTier(@PathVariable Long id, @RequestBody PricingTier tier) {
        return pricingService.updateTier(id, tier);
    }
}
