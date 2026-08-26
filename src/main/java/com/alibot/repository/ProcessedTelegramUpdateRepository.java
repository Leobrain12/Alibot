package com.alibot.repository;

import com.alibot.domain.ProcessedTelegramUpdate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedTelegramUpdateRepository extends JpaRepository<ProcessedTelegramUpdate, Long> {
}
