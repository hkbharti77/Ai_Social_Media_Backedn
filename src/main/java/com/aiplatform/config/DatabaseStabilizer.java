package com.aiplatform.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseStabilizer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void stabilize() {
        log.info("Checking database schema stability...");
        try {
            // Manually add price_amount to pricing_tiers if it doesn't exist
            jdbcTemplate.execute("ALTER TABLE pricing_tiers ADD COLUMN IF NOT EXISTS price_amount BIGINT DEFAULT 0");
            
            // Populate values for existing rows if they are 0
            jdbcTemplate.execute("UPDATE pricing_tiers SET price_amount = 999 WHERE name = 'Standard' AND price_amount = 0");
            jdbcTemplate.execute("UPDATE pricing_tiers SET price_amount = 3999 WHERE name = 'Pro' AND price_amount = 0");
            jdbcTemplate.execute("UPDATE pricing_tiers SET price_amount = 9999 WHERE name = 'Super Pro' AND price_amount = 0");
            
            log.info("Database stabilization complete: price_amount column verified and data populated.");
        } catch (Exception e) {
            log.error("Database stabilization failed: {}", e.getMessage());
        }
    }
}
