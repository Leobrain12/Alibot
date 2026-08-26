package com.alibot.api.dto;

import com.alibot.domain.OrderMedia;
import java.time.Instant;
import java.util.UUID;

public record OrderMediaResponse(
        UUID id,
        String mediaType,
        String stage,
        String mimeType,
        Long fileSize,
        Instant createdAt,
        String downloadUrl,
        boolean purged
) {
    public static OrderMediaResponse from(OrderMedia m) {
        boolean purged = m.getPurgedAt() != null;
        return new OrderMediaResponse(m.getId(), m.getMediaType().name(), m.getStage().name(),
                m.getMimeType(), m.getFileSize(), m.getCreatedAt(),
                purged ? null : "/api/v1/media/" + m.getId() + "/content", purged);
    }
}
