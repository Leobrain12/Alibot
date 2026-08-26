package com.alibot.api.dto;

import com.alibot.domain.Master;
import java.util.Set;
import java.util.UUID;

public record MasterResponse(
        UUID id,
        String name,
        String phone,
        String status,
        boolean active,
        Set<String> applianceTypes,
        Set<String> brands,
        Set<String> geoZones
) {
    public static MasterResponse from(Master m) {
        return new MasterResponse(m.getId(), m.getName(), m.getPhone(), m.getStatus().name(), m.isActive(),
                m.getApplianceTypes(), m.getBrands(), m.getGeoZones());
    }
}
