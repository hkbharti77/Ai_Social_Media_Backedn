package com.aiplatform.repository;

import com.aiplatform.model.AiJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiJobRepository extends JpaRepository<AiJob, Long> {
    Optional<AiJob> findByCorrelationId(String correlationId);

    List<AiJob> findTop10ByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
            AiJob.JobStatus status, LocalDateTime nextRetryAt);

    List<AiJob> findTop20ByStatusInOrderByCreatedAtDesc(List<AiJob.JobStatus> statuses);
}
