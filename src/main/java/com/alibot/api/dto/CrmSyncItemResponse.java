package com.alibot.api.dto;

import com.alibot.domain.CrmSyncQueueItem;
import java.time.Instant;
import java.util.UUID;

public record CrmSyncItemResponse(
        UUID id,
        UUID orderId,
        String eventType,
        String status,
        int attempts,
        String lastError,
        Instant nextAttemptAt,
        Instant updatedAt
) {
    public static CrmSyncItemResponse from(CrmSyncQueueItem i) {
        return new CrmSyncItemResponse(i.getId(), i.getOrderId(), i.getEventType(), i.getStatus().name(),
                i.getAttempts(), i.getLastError(), i.getNextAttemptAt(), i.getUpdatedAt());
    }
}
