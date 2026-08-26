package com.alibot.repository;

import com.alibot.domain.WorkReport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkReportRepository extends JpaRepository<WorkReport, UUID> {
    List<WorkReport> findByOrderIdOrderByCreatedAtDesc(UUID orderId);
    Optional<WorkReport> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
