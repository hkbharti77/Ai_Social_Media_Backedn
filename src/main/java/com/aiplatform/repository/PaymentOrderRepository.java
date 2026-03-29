package com.aiplatform.repository;

import com.aiplatform.model.PaymentOrder;
import com.aiplatform.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
    Optional<PaymentOrder> findByRazorpayOrderId(String razorpayOrderId);
    List<PaymentOrder> findByUserOrderByCreatedAtDesc(User user);
}
