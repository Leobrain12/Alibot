package com.alibot.repository;

import com.alibot.domain.OrderStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, UUID> {
    List<OrderStatusHistory> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    java.util.Optional<OrderStatusHistory> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
