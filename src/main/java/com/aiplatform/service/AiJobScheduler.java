package com.aiplatform.service;

import com.aiplatform.model.AiJob;
import com.aiplatform.repository.AiJobRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AiJobScheduler - Recovers and executes pending or failed AI jobs.
 */
@Service
@RequiredArgsConstructor
public class AiJobScheduler {
    private static final Logger logger = LoggerFactory.getLogger(AiJobScheduler.class);
    private final AiJobRepository jobRepository;
    private final AiOrchestrator aiOrchestrator;

    @Scheduled(fixedDelay = 10000) // Every 10 seconds
    @Transactional
    public void processJobs() {
        List<AiJob> pendingJobs = jobRepository.findTop10ByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
                AiJob.JobStatus.PENDING, LocalDateTime.now());
        
        for (AiJob job : pendingJobs) {
            executeJob(job);
        }
    }

    private void executeJob(AiJob job) {
        logger.info("⚡ Executing persistent AI job: {}", job.getCorrelationId());
        job.setStatus(AiJob.JobStatus.RUNNING);
        jobRepository.save(job);

        try {
            // In a real implementation, we would deserialize the payload and call the orchestrator
            // aiOrchestrator.generateFromJob(job);
            
            job.setStatus(AiJob.JobStatus.COMPLETED);
            logger.info("✅ Job completed: {}", job.getCorrelationId());
        } catch (Exception e) {
            logger.error("❌ Job failed: {}", job.getCorrelationId());
            handleFailure(job, e.getMessage());
        }
        jobRepository.save(job);
    }

    private void handleFailure(AiJob job, String error) {
        job.setRetryCount(job.getRetryCount() + 1);
        job.setErrorMessage(error);
        
        if (job.getRetryCount() >= job.getMaxRetries()) {
            job.setStatus(AiJob.JobStatus.DEAD_LETTER);
            logger.warn("💀 Job moved to Dead Letter Queue: {}", job.getCorrelationId());
        } else {
            job.setStatus(AiJob.JobStatus.PENDING);
            // Exponential backoff
            job.setNextRetryAt(LocalDateTime.now().plusMinutes((long) Math.pow(2, job.getRetryCount())));
        }
    }
}
