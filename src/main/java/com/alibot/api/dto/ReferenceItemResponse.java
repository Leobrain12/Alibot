package com.alibot.api.dto;

import com.alibot.domain.ReferenceItem;
import java.util.UUID;

public record ReferenceItemResponse(UUID id, String category, String value, int sortOrder, boolean active) {
    public static ReferenceItemResponse from(ReferenceItem item) {
        return new ReferenceItemResponse(item.getId(), item.getCategory().name(), item.getValue(),
                item.getSortOrder(), item.isActive());
    }
}
