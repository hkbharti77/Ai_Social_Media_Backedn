package com.aiplatform.repository;

import com.aiplatform.model.CreditUsage;
import com.aiplatform.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CreditUsageRepository extends JpaRepository<CreditUsage, Long> {
    List<CreditUsage> findByUserOrderByCreatedAtDesc(User user);
}
