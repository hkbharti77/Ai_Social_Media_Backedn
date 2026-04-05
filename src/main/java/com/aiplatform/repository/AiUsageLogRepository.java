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

    @Query("SELECT SUM(l.totalTokens) FROM AiUsageLog l")
    Long sumTotalTokens();

    @Query("SELECT l.modelId as modelId, SUM(l.promptTokens) as promptTokens, " +
           "SUM(l.completionTokens) as completionTokens, SUM(l.totalTokens) as totalTokens " +
           "FROM AiUsageLog l WHERE l.user = :user AND l.createdAt >= :startDate " +
           "GROUP BY l.modelId")
    List<Map<String, Object>> getUsageSummaryByUser(User user, LocalDateTime startDate);

    @Query("SELECT l.modelId as modelId, SUM(l.promptTokens) as promptTokens, " +
           "SUM(l.completionTokens) as completionTokens, SUM(l.totalTokens) as totalTokens, " +
           "COUNT(l) as callCount " +
           "FROM AiUsageLog l WHERE l.createdAt >= :startDate " +
           "GROUP BY l.modelId")
    List<Map<String, Object>> getModelUsageStats(LocalDateTime startDate);

    @Query("SELECT l.user.id as userId, l.user.email as email, l.user.fullName as fullName, " +
           "SUM(l.totalTokens) as totalTokens, l.user.loginCount as loginCount, " +
           "l.user.lastLoginAt as lastLoginAt " +
           "FROM AiUsageLog l " +
           "GROUP BY l.user.id, l.user.email, l.user.fullName, l.user.loginCount, l.user.lastLoginAt " +
           "ORDER BY SUM(l.totalTokens) DESC")
    List<Map<String, Object>> findTopTokenUsers();

    @Query("SELECT l.user.id as userId, l.modelId as modelId, SUM(l.totalTokens) as totalTokens " +
           "FROM AiUsageLog l WHERE l.user.id IN :userIds " +
           "GROUP BY l.user.id, l.modelId")
    List<Map<String, Object>> findModelUsageByUsers(List<Long> userIds);
}
