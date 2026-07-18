-- V2: Add CREATOR tier to all database constraints
-- Date: 2026-05-09
-- Purpose: Merge manual fix scripts into managed migration

-- 1. payment_orders table constraint
ALTER TABLE payment_orders 
DROP CONSTRAINT IF EXISTS payment_orders_target_tier_check;

ALTER TABLE payment_orders 
ADD CONSTRAINT payment_orders_target_tier_check 
CHECK (
    target_tier IS NULL OR 
    target_tier IN ('FREE', 'STANDARD', 'CREATOR', 'PRO', 'SUPER_PRO')
);

-- 2. users table constraint
ALTER TABLE users 
DROP CONSTRAINT IF EXISTS users_subscription_tier_check;

ALTER TABLE users 
ADD CONSTRAINT users_subscription_tier_check 
CHECK (
    subscription_tier IS NULL OR 
    subscription_tier IN ('FREE', 'STANDARD', 'CREATOR', 'PRO', 'SUPER_PRO')
);
