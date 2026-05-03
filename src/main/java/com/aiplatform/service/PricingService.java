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
        log.info("Checking and Syncing Enterprise Pricing Tiers (INR)...");

        syncTier("Free", "₹0", 0L,
                "Experience the magic of AI content creation.",
                10.0, 2,
                Arrays.asList(
                    "10 Image Credits / month",
                    "2 Posts per day",
                    "Gemini Flash models only",
                    "Unlimited Scripts",
                    "No video generation"
                ), false, 0);

        syncTier("Creator", "₹799", 799L,
                "Unlimited image creation for content creators.",
                400.0, -1,
                Arrays.asList(
                    "400 Image Credits / month",
                    "Unlimited Daily Posts",
                    "4 Image AI Models (up to Imagen 4 Standard)",
                    "Unlimited Scripts",
                    "No video generation"
                ), false, 1);

        syncTier("Standard", "₹499", 499L,
                "Elevate your social presence with AI images and videos.",
                200.0, 20,
                Arrays.asList(
                    "200 Image Credits / month",
                    "20 Posts per day",
                    "4 Image AI Models (up to Imagen 4 Standard)",
                    "5 Veo Lite Video Credits included",
                    "Buy more video credits anytime"
                ), false, 2);

        syncTier("Pro", "₹1,499", 1499L,
                "Scale your brand with high-volume AI intelligence.",
                1000.0, -1,
                Arrays.asList(
                    "1,000 Image Credits / month",
                    "Unlimited Daily Posts",
                    "All 6 Image AI Models (incl. Imagen Ultra)",
                    "8 Veo Lite + 2 Veo Fast Video Credits included",
                    "Buy more video credits anytime"
                ), true, 3);

        syncTier("Super Pro", "₹5,999", 5999L,
                "Enterprise-grade power for massive content operations.",
                2000.0, -1,
                Arrays.asList(
                    "2,000 Image Credits / month",
                    "Unlimited Daily Posts",
                    "All 6 Image AI Models (incl. Gemini 3 Pro)",
                    "10 Lite + 5 Fast + 2 Standard Video Credits included",
                    "Buy more video credits anytime"
                ), false, 4);

        log.info("Pricing Synchronization complete.");
    }

    private void syncTier(String name, String priceInr, Long priceAmount, String description, 
                          Double monthlyCredits, Integer dailyLimit, List<String> features, Boolean popular, Integer ordinal) {
        PricingTier tier = pricingRepository.findAll().stream()
                .filter(t -> t.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(new PricingTier());

        tier.setName(name);
        tier.setPriceInr(priceInr);
        tier.setPriceAmount(priceAmount);
        tier.setDescription(description);
        tier.setMonthlyCredits(monthlyCredits);
        tier.setDailyLimit(dailyLimit);
        tier.setFeatures(features);
        tier.setPopular(popular);
        tier.setTierOrdinal(ordinal);

        pricingRepository.save(tier);
    }

    @Transactional
    public PricingTier updateTier(Long id, PricingTier update) {
        return pricingRepository.findById(id)
                .map(existing -> {
                    if (update.getPriceInr() != null) existing.setPriceInr(update.getPriceInr());
                    if (update.getPriceAmount() != null) existing.setPriceAmount(update.getPriceAmount());
                    if (update.getDescription() != null) existing.setDescription(update.getDescription());
                    if (update.getMonthlyCredits() != null) existing.setMonthlyCredits(update.getMonthlyCredits());
                    if (update.getDailyLimit() != null) existing.setDailyLimit(update.getDailyLimit());
                    if (update.getMaxProfiles() != null) existing.setMaxProfiles(update.getMaxProfiles());
                    if (update.getPopular() != null) existing.setPopular(update.getPopular());
                    if (update.getFeatures() != null) existing.setFeatures(update.getFeatures());
                    return pricingRepository.save(existing);
                })
                .orElseThrow(() -> new RuntimeException("Pricing Tier not found: " + id));
    }
}
