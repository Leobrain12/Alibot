package com.alibot.repository;

import com.alibot.domain.CrmSyncQueueItem;
import com.alibot.domain.CrmSyncStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrmSyncQueueRepository extends JpaRepository<CrmSyncQueueItem, UUID> {

    List<CrmSyncQueueItem> findByStatusAndNextAttemptAtBefore(CrmSyncStatus status, Instant now);

    List<CrmSyncQueueItem> findByStatusOrderByUpdatedAtDesc(CrmSyncStatus status);
}
