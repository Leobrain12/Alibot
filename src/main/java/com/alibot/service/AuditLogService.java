package com.alibot.service;

import com.alibot.domain.AuditLog;
import com.alibot.repository.AuditLogRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ТЗ п.95 — общий журнал действий. Пишется из OrderService/PaymentService/WorkReportService/
 * MediaService в конкретных точках изменения (см. вызовы record(...)), а не через
 * АОП-перехват всего подряд — так каждый вызов явно говорит, что именно и почему логируется.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AuditLogService {

    private final AuditLogRepository repository;
    private final AccessControlService accessControl;

    public void record(String action, String entityType, UUID entityId, UUID actorUserId,
                        String oldValue, String newValue) {
        repository.save(AuditLog.builder()
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .actorUserId(actorUserId)
                .oldValue(oldValue)
                .newValue(newValue)
                .build());
    }

    @Transactional(readOnly = true)
    public List<AuditLog> forEntity(String entityType, UUID entityId, AuthenticatedActor actor) {
        accessControl.assertIsAdmin(actor);
        return repository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> recent(AuthenticatedActor actor) {
        accessControl.assertIsAdmin(actor);
        return repository.findTop200ByOrderByCreatedAtDesc();
    }
}
