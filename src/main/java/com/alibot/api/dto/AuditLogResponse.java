package com.alibot.api.dto;

import com.alibot.domain.AuditLog;
import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID actorUserId,
        String action,
        String entityType,
        UUID entityId,
        String oldValue,
        String newValue,
        Instant createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getActorUserId(), log.getAction(),
                log.getEntityType(), log.getEntityId(), log.getOldValue(), log.getNewValue(), log.getCreatedAt());
    }
}
