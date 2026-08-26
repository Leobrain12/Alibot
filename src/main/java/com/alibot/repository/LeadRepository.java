package com.alibot.repository;

import com.alibot.domain.Lead;
import com.alibot.domain.LeadStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadRepository extends JpaRepository<Lead, UUID> {
    List<Lead> findByStatusOrderByCreatedAtAsc(LeadStatus status);
    List<Lead> findAllByOrderByCreatedAtDesc();
}
