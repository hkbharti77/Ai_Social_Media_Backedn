-- V3: Fix Pricing Tiers Data and Stability
-- Date: 2026-05-09
-- Purpose: Merge DatabaseStabilizer logic and pro plan ordinal fixes

-- Ensure price_amount exists (V1 already includes it, but just in case for older environments)
-- Note: In a fresh DB V1 handles it, in existing DB Flyway baseline handles it.
-- We'll keep it safe with data updates.

-- Fix all tier ordinals to ensure correct hierarchy
UPDATE pricing_tiers SET tier_ordinal = 0 WHERE name = 'Free';
UPDATE pricing_tiers SET tier_ordinal = 1 WHERE name = 'Creator';
UPDATE pricing_tiers SET tier_ordinal = 2 WHERE name = 'Standard';
UPDATE pricing_tiers SET tier_ordinal = 3 WHERE name = 'Pro';
UPDATE pricing_tiers SET tier_ordinal = 4 WHERE name = 'Super Pro';

-- Fix price_amount values if they are 0 or missing
UPDATE pricing_tiers SET price_amount = 0 WHERE name = 'Free' AND price_amount IS NULL;
UPDATE pricing_tiers SET price_amount = 799 WHERE name = 'Creator' AND (price_amount IS NULL OR price_amount = 0);
UPDATE pricing_tiers SET price_amount = 499 WHERE name = 'Standard' AND (price_amount IS NULL OR price_amount = 0);
UPDATE pricing_tiers SET price_amount = 1499 WHERE name = 'Pro' AND (price_amount IS NULL OR price_amount = 0);
UPDATE pricing_tiers SET price_amount = 5999 WHERE name = 'Super Pro' AND (price_amount IS NULL OR price_amount = 0);
