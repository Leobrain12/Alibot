package com.alibot.service.dto;

import java.math.BigDecimal;

/** ТЗ п.60-62 — полная либо частичная оплата. */
public record PaymentCommand(BigDecimal amount, String paymentType) {
}
