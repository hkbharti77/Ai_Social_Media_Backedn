package com.aiplatform.repository;

import com.aiplatform.model.PaymentOrder;
import com.aiplatform.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
    Optional<PaymentOrder> findByRazorpayOrderId(String razorpayOrderId);
    List<PaymentOrder> findByUserOrderByCreatedAtDesc(User user);

    // Admin Management - Paginated all-payments with optional status filter
    Page<PaymentOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<PaymentOrder> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    // Admin Management - Revenue aggregation
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentOrder p WHERE p.status = 'COMPLETED'")
    Long sumCompletedRevenue();

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentOrder p " +
           "WHERE p.status = 'COMPLETED' AND p.completedAt >= :startOfMonth")
    Long sumCompletedRevenueFrom(@Param("startOfMonth") LocalDateTime startOfMonth);

    @Query("SELECT COUNT(p) FROM PaymentOrder p WHERE p.status = 'COMPLETED'")
    Long countCompleted();

    @Query("SELECT COUNT(p) FROM PaymentOrder p " +
           "WHERE p.status = 'COMPLETED' AND p.completedAt >= :startOfMonth")
    Long countCompletedFrom(@Param("startOfMonth") LocalDateTime startOfMonth);
}
