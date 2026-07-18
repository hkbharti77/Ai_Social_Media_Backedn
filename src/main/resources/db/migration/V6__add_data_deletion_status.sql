-- Add facebook_user_id to social_accounts
ALTER TABLE social_accounts ADD COLUMN facebook_user_id VARCHAR(255);

-- Create data_deletion_status table
CREATE TABLE data_deletion_status (
    id BIGSERIAL PRIMARY KEY,
    confirmation_code VARCHAR(255) NOT NULL UNIQUE,
    facebook_user_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    requested_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
