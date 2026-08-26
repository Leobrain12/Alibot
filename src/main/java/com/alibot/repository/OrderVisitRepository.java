package com.alibot.repository;

import com.alibot.domain.OrderVisit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderVisitRepository extends JpaRepository<OrderVisit, UUID> {
    List<OrderVisit> findByOrderIdOrderByVisitNumberAsc(UUID orderId);
    long countByOrderId(UUID orderId);
}
