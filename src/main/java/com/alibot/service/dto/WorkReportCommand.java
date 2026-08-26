package com.alibot.service.dto;

import java.math.BigDecimal;

/** ТЗ п.40-46 — обязательный отчёт мастера при завершении ремонта. */
public record WorkReportCommand(
        String workDescription,
        BigDecimal laborPrice,
        BigDecimal partsSellPrice,
        BigDecimal partsCost,
        BigDecimal masterPayout,
        String comment
) {
}
