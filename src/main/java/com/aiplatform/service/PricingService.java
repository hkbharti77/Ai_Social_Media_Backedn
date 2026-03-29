package com.aiplatform.service;

import com.aiplatform.model.PricingTier;
import com.aiplatform.repository.PricingRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PricingService {

    private final PricingRepository pricingRepository;

    public List<PricingTier> getAllTiers() {
        return pricingRepository.findAll();
    }

    @PostConstruct
    @Transactional
    public void initDefaultTiers() {
        if (pricingRepository.count() == 0) {
            log.info("Initializing default Enterprise Pricing Tiers (INR)...");

            pricingRepository.save(PricingTier.builder()
                    .name("Free")
                    .priceInr("₹0")
                    .priceAmount(0L)
                    .description("Experience the magic of AI content creation.")
                    .monthlyCredits(10)
                    .dailyLimit(2)
                    .features(Arrays.asList("10 AI Credits / month", "2 Posts per day", "Standard AI Model"))
                    .popular(false)
                    .build());

            pricingRepository.save(PricingTier.builder()
                    .name("Standard")
                    .priceInr("₹999")
                    .priceAmount(999L)
                    .description("Elevate your social presence with consistent AI output.")
                    .monthlyCredits(100)
                    .dailyLimit(20)
                    .features(Arrays.asList("100 AI Credits / month", "20 Posts per day", "Brand Voice Training"))
                    .popular(false)
                    .build());

            pricingRepository.save(PricingTier.builder()
                    .name("Pro")
                    .priceInr("₹3,999")
                    .priceAmount(3999L)
                    .description("Scale your brand with high-volume AI intelligence.")
                    .monthlyCredits(1000)
                    .dailyLimit(-1)
                    .features(Arrays.asList("1,000 AI Credits / month", "Unlimited Daily Posts", "Premium Imagen 3 Model"))
                    .popular(true)
                    .build());

            pricingRepository.save(PricingTier.builder()
                    .name("Super Pro")
                    .priceInr("₹7,999")
                    .priceAmount(7999L)
                    .description("Enterprise-grade power for massive content operations.")
                    .monthlyCredits(20000)
                    .dailyLimit(-1) 
                    .features(Arrays.asList("20,000 AI Credits / month", "Unlimited Daily Posts", "Ultra-HD Image Exports"))
                    .popular(false)
                    .build());

            log.info("Pricing Initialization complete.");
        }
    }
}
