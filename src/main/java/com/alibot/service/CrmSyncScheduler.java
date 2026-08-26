package com.alibot.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** ТЗ п.85-87 — фоновая обработка очереди CRM-синхронизации: подхватывает записи, у которых
 *  наступило время следующей попытки, и пробует доставить их через CrmSyncGateway. */
@Component
@RequiredArgsConstructor
public class CrmSyncScheduler {

    private final CrmSyncService crmSyncService;

    @Scheduled(cron = "${app.crm.retry-cron}")
    public void processQueue() {
        List<UUID> pending = crmSyncService.pendingIds(Instant.now());
        for (UUID id : pending) {
            crmSyncService.attemptDelivery(id);
        }
    }
}
