package com.aiplatform.service;

import com.aiplatform.dto.AiRequest;
import com.aiplatform.model.AiTaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

/**
 * AiTaskDispatcher - Routes AI requests to the appropriate specialized executor pool.
 */
@Service
public class AiTaskDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(AiTaskDispatcher.class);

    private final Executor syncExecutor;
    private final Executor backgroundExecutor;

    public AiTaskDispatcher(@Qualifier("syncAiTaskExecutor") Executor syncExecutor,
                            @Qualifier("backgroundAiTaskExecutor") Executor backgroundExecutor) {
        this.syncExecutor = syncExecutor;
        this.backgroundExecutor = backgroundExecutor;
    }

    public void dispatch(AiRequest request, Runnable task) {
        AiTaskType type = request.getTaskType();
        if (type == null) type = AiTaskType.SYNC_USER_REQUEST;

        logger.info("📡 Dispatching task [Type: {}, ID: {}]", type, request.getCorrelationId());

        if (type == AiTaskType.SYNC_USER_REQUEST) {
            syncExecutor.execute(task);
        } else if (type == AiTaskType.BACKGROUND_CAMPAIGN) {
            backgroundExecutor.execute(task);
        } else {
            // Analytics or Low priority can be handled with even lower resource allocation
            backgroundExecutor.execute(task);
        }
    }
}
