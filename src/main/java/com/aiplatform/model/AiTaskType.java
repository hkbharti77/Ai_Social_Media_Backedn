package com.aiplatform.model;

/**
 * AiTaskType - Defines the priority and execution semantics for an AI task.
 */
public enum AiTaskType {
    /**
     * High priority, user-facing. Rejects immediately if the system is overloaded.
     */
    SYNC_USER_REQUEST,

    /**
     * Medium priority. Can wait in the queue. Retries are prioritized.
     */
    BACKGROUND_CAMPAIGN,

    /**
     * Low priority. Can be dropped or delayed significantly during peak loads.
     */
    ANALYTICS_ENRICHMENT
}
