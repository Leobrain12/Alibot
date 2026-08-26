package com.alibot.service.dto;

import java.math.BigDecimal;

/** ТЗ п.32.1 — поля при переводе заказа в WAITING_PART. */
public record WaitingPartCommand(
        String partName,
        String partNumber,
        BigDecimal estimatedPurchasePrice,
        String comment
) {
}
