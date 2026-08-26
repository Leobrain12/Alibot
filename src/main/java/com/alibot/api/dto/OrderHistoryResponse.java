package com.alibot.api.dto;

import com.alibot.domain.OrderStatusHistory;
import java.time.Instant;
import java.util.UUID;

/** ТЗ Figma #11 (Timeline) — одна запись истории статусов заказа для отображения в интерфейсе. */
public record OrderHistoryResponse(
        UUID id,
        String oldStatus,
        String newStatus,
        String changedByName,
        String comment,
        Instant createdAt
) {
    public static OrderHistoryResponse from(OrderStatusHistory h, String changedByName) {
        return new OrderHistoryResponse(h.getId(),
                h.getOldStatus() != null ? h.getOldStatus().name() : null,
                h.getNewStatus().name(),
                changedByName,
                h.getComment(),
                h.getCreatedAt());
    }
}
