package com.alibot.api.dto;

import com.alibot.domain.Lead;
import java.time.Instant;
import java.util.UUID;

public record LeadResponse(
        UUID id,
        String customerName,
        String customerPhone,
        String applianceType,
        String comment,
        String source,
        String externalId,
        String status,
        UUID convertedOrderId,
        String rejectReason,
        Instant createdAt
) {
    public static LeadResponse from(Lead l) {
        return new LeadResponse(l.getId(), l.getCustomerName(), l.getCustomerPhone(), l.getApplianceType(),
                l.getComment(), l.getSource(), l.getExternalId(), l.getStatus().name(),
                l.getConvertedOrderId(), l.getRejectReason(), l.getCreatedAt());
    }
}
