package com.alibot.service.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/** ТЗ п.35 — перенос заказа: обязательны новая дата, слот и причина. */
public record RescheduleCommand(
        LocalDate newDate,
        LocalTime newFrom,
        LocalTime newTo,
        String reason
) {
}
