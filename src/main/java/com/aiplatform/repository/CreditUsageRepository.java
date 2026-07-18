package com.aiplatform.repository;

import com.aiplatform.model.CreditUsage;
import com.aiplatform.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface CreditUsageRepository extends JpaRepository<CreditUsage, Long> {

    // Paginated history with optional user filter
    Page<CreditUsage> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<CreditUsage> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
    
    // Non-paginated method for bulk operations
    List<CreditUsage> findByUserOrderByCreatedAtDesc(User user);

    // Weekly report query
    List<CreditUsage> findByUserAndCreatedAtAfter(User user, java.time.LocalDateTime after);

    // Platform-wide stats
    @Query("SELECT COALESCE(SUM(c.amount), 0.0) FROM CreditUsage c")
    Double sumAllCredits();

    @Query("SELECT COALESCE(SUM(c.amount), 0.0) FROM CreditUsage c WHERE c.createdAt >= :startOfMonth")
    Double sumCreditsFrom(@Param("startOfMonth") LocalDateTime startOfMonth);

    @Query("SELECT c.user.id as userId, c.user.email as email, SUM(c.amount) as totalCredits " +
           "FROM CreditUsage c GROUP BY c.user.id, c.user.email " +
           "ORDER BY SUM(c.amount) DESC")
    List<Map<String, Object>> findTopCreditUsers(Pageable pageable);

    @Query("SELECT c.purpose as purpose, SUM(c.amount) as total " +
           "FROM CreditUsage c GROUP BY c.purpose")
    List<Map<String, Object>> sumCreditsByPurpose();
}
