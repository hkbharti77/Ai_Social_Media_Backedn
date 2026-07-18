package com.aiplatform.repository;

import com.aiplatform.model.LoginEvent;
import com.aiplatform.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LoginEventRepository extends JpaRepository<LoginEvent, Long> {

    /** All login events for a user, newest first */
    List<LoginEvent> findByUserOrderByCreatedAtDesc(User user);

    /** Paginated login history for a user */
    Page<LoginEvent> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    /** Check if this IP has ever been used by this user before */
    boolean existsByUserAndIpAddress(User user, String ipAddress);

    /** Recent successful logins for a user */
    @Query("SELECT e FROM LoginEvent e WHERE e.user = :user AND e.status = 'SUCCESS' ORDER BY e.createdAt DESC")
    List<LoginEvent> findRecentSuccessfulLogins(@Param("user") User user, Pageable pageable);

    /** Count failed logins in last N minutes (for brute-force detection) */
    @Query("SELECT COUNT(e) FROM LoginEvent e WHERE e.user = :user AND e.status = 'FAILED' AND e.createdAt >= :since")
    long countFailedLoginsAfter(@Param("user") User user, @Param("since") LocalDateTime since);

    /** Delete old login events (for data retention) */
    void deleteByCreatedAtBefore(LocalDateTime cutoff);
}
