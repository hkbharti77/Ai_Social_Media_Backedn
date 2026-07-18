CREATE TABLE ai_jobs (
    id BIGSERIAL PRIMARY KEY,
    correlation_id VARCHAR(255) NOT NULL,
    task_type VARCHAR(50),
    status VARCHAR(50),
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    request_payload_json TEXT,
    result_json TEXT,
    error_message TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    next_retry_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX idx_ai_jobs_status_retry ON ai_jobs(status, next_retry_at);
CREATE INDEX idx_ai_jobs_correlation ON ai_jobs(correlation_id);
