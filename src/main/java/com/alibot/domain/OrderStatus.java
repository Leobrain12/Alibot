package com.alibot.domain;

/** Статусы заказа — ТЗ п.14 (основная цепочка) и п.14.1 (дополнительные статусы). */
public enum OrderStatus {
    NEW,
    ASSIGNED,
    ACCEPTED,
    ON_THE_WAY,
    ARRIVED,
    DIAGNOSTICS,
    PRICE_APPROVAL,
    IN_PROGRESS,
    WAITING_PART,
    COMPLETED,
    PAID,

    MASTER_DECLINED,
    RESCHEDULED,
    CUSTOMER_CANCELLED,
    NO_CONTACT,
    UNREPAIRABLE,
    CANCELLED,
    WARRANTY_RETURN;

    /** Финальные статусы: дальнейших действий по заказу не требуется (в отличие от MASTER_DECLINED,
     *  который требует переназначения, или NO_CONTACT, который требует решения администратора). */
    public boolean isTerminal() {
        return this == PAID || this == CANCELLED || this == CUSTOMER_CANCELLED || this == UNREPAIRABLE;
    }
}
