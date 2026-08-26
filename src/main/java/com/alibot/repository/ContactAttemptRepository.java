package com.alibot.repository;

import com.alibot.domain.ContactAttempt;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactAttemptRepository extends JpaRepository<ContactAttempt, UUID> {
    List<ContactAttempt> findByOrderIdOrderByAttemptedAtDesc(UUID orderId);
    long countByOrderId(UUID orderId);
}
