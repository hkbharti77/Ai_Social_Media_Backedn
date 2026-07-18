package com.aiplatform.controller;

import com.aiplatform.model.AiJob;
import com.aiplatform.repository.AiJobRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * AiJobController - Provides real-time status updates for persistent AI tasks.
 * Endpoint is intentionally public so the UI can poll without a token.
 */
@RestController
@RequestMapping("/api/v1/ai/jobs")
@RequiredArgsConstructor
public class AiJobController {

    private static final Logger log = LoggerFactory.getLogger(AiJobController.class);

    private final AiJobRepository jobRepository;

    @GetMapping("/active")
    public ResponseEntity<List<AiJob>> getActiveJobs() {
        try {
            // Returns PENDING + RUNNING jobs (most recent 20).
            List<AiJob> jobs = jobRepository.findTop20ByStatusInOrderByCreatedAtDesc(
                    List.of(AiJob.JobStatus.PENDING, AiJob.JobStatus.RUNNING)
            );
            return ResponseEntity.ok(jobs);
        } catch (Exception e) {
            log.warn("Could not fetch active AI jobs — returning empty list. Cause: {}", e.getMessage());
            return ResponseEntity.ok(Collections.emptyList());
        }
    }
}
