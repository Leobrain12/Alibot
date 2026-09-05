package com.alibot.domain;

/** ТЗ п.18.1/132 — справочники backend'а, не хардкод в клиентах (боте/Mini App). */
public enum ReferenceCategory {
    APPLIANCE_TYPE("Типы техники"),
    BRAND("Бренды"),
    TIME_SLOT("Временные слоты"),
    MASTER_DECLINE_REASON("Причины отказа мастера"),
    CUSTOMER_CANCEL_REASON("Причины отказа клиента"),
    RESCHEDULE_REASON("Причины переноса");

    private final String label;

    ReferenceCategory(String label) {
        this.label = label;
    }

    /** Русская подпись — та же, что в Mini App (REFERENCE_CATEGORY_LABELS), чтобы бот и Mini App
     *  показывали справочники одинаково. */
    public String label() {
        return label;
    }
}
