package com.alibot.domain;

/** ТЗ п.44 — как считается выплата мастеру. */
public enum CommissionType {
    /** Выплата вводится мастером/админом вручную на каждом заказе. */
    MANUAL,
    /** Фиксированная сумма за заказ (commission_value). */
    FIXED,
    /** Процент от final_price (commission_value — целое число процентов). */
    PERCENT
}
