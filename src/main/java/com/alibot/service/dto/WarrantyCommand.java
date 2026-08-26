package com.alibot.service.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** ТЗ п.64 — создание гарантийного обращения по исходному заказу. */
public record WarrantyCommand(
        UUID originalOrderId,
        String problem,
        LocalDate visitDate,
        LocalTime timeFrom,
        LocalTime timeTo,
        UUID masterId,
        String comment
) {
}
