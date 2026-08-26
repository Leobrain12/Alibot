package com.alibot.service.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** ТЗ п.18 (шаги мастера создания заявки) / п.12 (поля Order). */
public record CreateOrderCommand(
        String customerName,
        String customerPhone,
        String applianceType,
        String brand,
        String model,
        String symptom,
        String description,
        String address,
        LocalDate visitDate,
        LocalTime timeFrom,
        LocalTime timeTo,
        UUID masterId,
        String adminComment,
        String leadId,
        String crmId,
        String source
) {
}
