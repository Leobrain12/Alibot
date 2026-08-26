package com.alibot.service.dto;

import java.math.BigDecimal;

/** ТЗ п.28/29 — мастер вводит причину, работы и цену после диагностики "можно ремонтировать". */
public record PriceApprovalCommand(
        String failureReason,
        String workNeeded,
        BigDecimal laborPrice,
        BigDecimal partsSellPrice
) {
}
