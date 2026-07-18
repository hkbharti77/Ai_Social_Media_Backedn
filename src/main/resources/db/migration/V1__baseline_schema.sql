-- V1: Baseline Schema (Snapshot of current entity-based structure)
-- Date: 2026-05-09

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    full_name VARCHAR(255),
    password VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    subscription_tier VARCHAR(255) DEFAULT 'FREE',
    monthly_credits DOUBLE PRECISION DEFAULT 10.0,
    bonus_credits DOUBLE PRECISION DEFAULT 0.0,
    referral_code VARCHAR(255) UNIQUE,
    referred_by VARCHAR(255),
    daily_ads_viewed INTEGER DEFAULT 0,
    last_ad_viewed_at TIMESTAMP,
    last_ad_started_at TIMESTAMP,
    registration_ip VARCHAR(255),
    device_fingerprint VARCHAR(255),
    is_fraud_flagged BOOLEAN DEFAULT FALSE,
    referral_status VARCHAR(255) DEFAULT 'PENDING',
    daily_credits_used DOUBLE PRECISION DEFAULT 0.0,
    last_generation_at TIMESTAMP,
    last_reset_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    subscription_expires_at TIMESTAMP,
    failed_login_attempts INTEGER DEFAULT 0 NOT NULL,
    lock_time TIMESTAMP,
    email_verified BOOLEAN DEFAULT TRUE NOT NULL,
    verification_token VARCHAR(255),
    stored_images_count INTEGER DEFAULT 0,
    stored_videos_count INTEGER DEFAULT 0,
    last_login_at TIMESTAMP,
    login_count BIGINT DEFAULT 0,
    total_usage_minutes BIGINT DEFAULT 0,
    monthly_video_limit INTEGER DEFAULT 0,
    videos_used_this_month INTEGER DEFAULT 0,
    video_reset_date TIMESTAMP,
    video_credits_lite INTEGER DEFAULT 0,
    video_credits_fast INTEGER DEFAULT 0,
    video_credits_standard INTEGER DEFAULT 0,
    password_reset_token VARCHAR(255),
    password_reset_token_expiry TIMESTAMP,
    last_login_ip VARCHAR(255),
    last_login_user_agent TEXT
);

-- 2. User Roles (Collection Table)
CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id),
    role VARCHAR(255),
    PRIMARY KEY (user_id, role)
);

-- 3. User Purchased Models (Collection Table)
CREATE TABLE IF NOT EXISTS user_purchased_models (
    user_id BIGINT NOT NULL REFERENCES users(id),
    model_id VARCHAR(255),
    PRIMARY KEY (user_id, model_id)
);

-- 4. Refresh Tokens Table
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE REFERENCES users(id),
    token VARCHAR(255) UNIQUE NOT NULL,
    expiry_date TIMESTAMPTZ NOT NULL
);

-- 5. Business Profiles Table
CREATE TABLE IF NOT EXISTS business_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    business_name VARCHAR(255),
    brand_slug VARCHAR(255) UNIQUE,
    niche VARCHAR(255),
    target_audience VARCHAR(255),
    brand_tone VARCHAR(255),
    posting_frequency INTEGER,
    preferred_hashtags VARCHAR(1000),
    image_style VARCHAR(255),
    people_preference VARCHAR(255),
    brand_mood VARCHAR(255),
    design_style VARCHAR(255),
    visual_constraints VARCHAR(255),
    image_type VARCHAR(255),
    composition_style VARCHAR(255),
    camera_angle VARCHAR(255),
    lighting_style VARCHAR(255),
    color_temperature VARCHAR(255),
    background_style VARCHAR(255),
    subject_focus VARCHAR(255),
    logo_placement VARCHAR(255),
    aspect_ratio VARCHAR(255),
    quality_level VARCHAR(255),
    creativity_level DOUBLE PRECISION,
    reference_image_url VARCHAR(255),
    negative_prompt VARCHAR(255),
    morning_draft_time VARCHAR(255) DEFAULT '06:00',
    evening_draft_time VARCHAR(255) DEFAULT '15:00',
    morning_publish_time VARCHAR(255) DEFAULT '09:00',
    evening_publish_time VARCHAR(255) DEFAULT '20:00',
    use_ai_best_time BOOLEAN DEFAULT FALSE,
    gemini_cache_id VARCHAR(255),
    gemini_cache_expiry TIMESTAMP,
    gemini_cache_content_hash VARCHAR(255),
    last_scraped_cache_id VARCHAR(255),
    last_scraped_expiry TIMESTAMP,
    last_scraped_hash VARCHAR(255),
    -- Embedded TextOverlay fields
    enabled BOOLEAN,
    style VARCHAR(255),
    position VARCHAR(255),
    default_voice_mode VARCHAR(255) DEFAULT 'STYLE_DNA',
    brand_style_dna VARCHAR(2000)
);

-- 6. Profile Brand Colors (Collection Table)
CREATE TABLE IF NOT EXISTS profile_brand_colors (
    profile_id BIGINT NOT NULL REFERENCES business_profiles(id),
    color VARCHAR(255)
);

-- 7. Profile Voice Samples (Collection Table)
CREATE TABLE IF NOT EXISTS profile_voice_samples (
    profile_id BIGINT NOT NULL REFERENCES business_profiles(id),
    sample_text VARCHAR(2000)
);

-- 8. Profile Voice Images (Collection Table)
CREATE TABLE IF NOT EXISTS profile_voice_images (
    profile_id BIGINT NOT NULL REFERENCES business_profiles(id),
    image_url VARCHAR(255)
);

-- 9. Posts Table
CREATE TABLE IF NOT EXISTS posts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    caption TEXT,
    image_url TEXT,
    video_url TEXT,
    hashtags TEXT,
    status VARCHAR(255) NOT NULL,
    platform VARCHAR(255),
    slot_type VARCHAR(255),
    auto_scheduled BOOLEAN DEFAULT FALSE,
    scheduled_at TIMESTAMP,
    published_at TIMESTAMP,
    external_post_id TEXT,
    failure_reason TEXT,
    is_thread BOOLEAN DEFAULT FALSE,
    thread_content TEXT,
    is_carousel BOOLEAN DEFAULT FALSE,
    carousel_content TEXT,
    is_story BOOLEAN DEFAULT FALSE,
    is_poll BOOLEAN DEFAULT FALSE,
    is_reel BOOLEAN DEFAULT FALSE,
    video_script TEXT,
    poll_content TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_evergreen BOOLEAN DEFAULT FALSE,
    evergreen_score DOUBLE PRECISION DEFAULT 0.0,
    last_recycled_at TIMESTAMP
);

-- 10. Comments Table
CREATE TABLE IF NOT EXISTS comments (
    id BIGSERIAL PRIMARY KEY,
    external_comment_id VARCHAR(255),
    text TEXT NOT NULL,
    author_name VARCHAR(255),
    author_profile_picture_url VARCHAR(1000),
    platform VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_replied BOOLEAN DEFAULT FALSE,
    ai_draft_reply TEXT,
    sentiment VARCHAR(255),
    priority VARCHAR(255),
    user_id BIGINT NOT NULL REFERENCES users(id),
    post_id BIGINT REFERENCES posts(id)
);

-- 11. Social Accounts Table
CREATE TABLE IF NOT EXISTS social_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    platform VARCHAR(255) NOT NULL,
    encrypted_token VARCHAR(1000),
    page_id VARCHAR(255),
    ig_business_id VARCHAR(255),
    account_name VARCHAR(255),
    profile_picture_url VARCHAR(1000),
    token_expires_at TIMESTAMP,
    encrypted_refresh_token VARCHAR(1000),
    connected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 12. Payment Orders Table
CREATE TABLE IF NOT EXISTS payment_orders (
    id BIGSERIAL PRIMARY KEY,
    razorpay_order_id VARCHAR(255) UNIQUE NOT NULL,
    razorpay_payment_id VARCHAR(255),
    razorpay_signature VARCHAR(255),
    user_id BIGINT NOT NULL REFERENCES users(id),
    target_tier VARCHAR(255),
    amount BIGINT,
    currency VARCHAR(255),
    order_type VARCHAR(255) DEFAULT 'SUBSCRIPTION',
    video_model_id VARCHAR(255),
    video_credits_purchased INTEGER,
    status VARCHAR(255) DEFAULT 'CREATED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

-- 13. Credit Usage Table
CREATE TABLE IF NOT EXISTS credit_usage (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    amount DOUBLE PRECISION NOT NULL,
    purpose VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reference_id BIGINT
);

-- 14. AI Usage Logs Table
CREATE TABLE IF NOT EXISTS ai_usage_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    model_id VARCHAR(255) NOT NULL,
    action_type VARCHAR(255) NOT NULL,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_tokens INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    prompt TEXT,
    result_url VARCHAR(255),
    feature_name VARCHAR(255)
);

-- 15. Login Events Table
CREATE TABLE IF NOT EXISTS login_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    ip_address VARCHAR(255),
    user_agent TEXT,
    browser VARCHAR(255),
    operating_system VARCHAR(255),
    device_type VARCHAR(255),
    status VARCHAR(255) NOT NULL,
    is_new_ip BOOLEAN DEFAULT FALSE,
    failure_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_login_events_user_id ON login_events(user_id);
CREATE INDEX IF NOT EXISTS idx_login_events_created_at ON login_events(created_at);

-- 16. Microsite Links Table
CREATE TABLE IF NOT EXISTS microsite_links (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(255) NOT NULL,
    url VARCHAR(1000) NOT NULL,
    click_count INTEGER DEFAULT 0,
    sort_order INTEGER DEFAULT 0,
    icon VARCHAR(255),
    active BOOLEAN DEFAULT TRUE
);

-- 17. Pricing Tiers Table
CREATE TABLE IF NOT EXISTS pricing_tiers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    price_inr VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    price_amount BIGINT NOT NULL,
    monthly_credits DOUBLE PRECISION,
    daily_limit INTEGER,
    max_profiles INTEGER,
    popular BOOLEAN,
    tier_ordinal INTEGER
);

-- 18. Tier Features (Collection Table)
CREATE TABLE IF NOT EXISTS tier_features (
    tier_id BIGINT NOT NULL REFERENCES pricing_tiers(id),
    feature VARCHAR(255)
);
