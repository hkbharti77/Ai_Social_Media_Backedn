package com.aiplatform.repository;

import com.aiplatform.model.AiUsageLog;
import com.aiplatform.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    List<AiUsageLog> findByUserOrderByCreatedAtDesc(User user);

    @Query("SELECT l.modelId as modelId, SUM(l.promptTokens) as promptTokens, " +
           "SUM(l.completionTokens) as completionTokens, SUM(l.totalTokens) as totalTokens " +
           "FROM AiUsageLog l WHERE l.user = :user AND l.createdAt >= :startDate " +
           "GROUP BY l.modelId")
    List<Map<String, Object>> getUsageSummaryByUser(User user, LocalDateTime startDate);
}
