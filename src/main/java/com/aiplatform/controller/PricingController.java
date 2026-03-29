package com.aiplatform.controller;

import com.aiplatform.model.PricingTier;
import com.aiplatform.service.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
