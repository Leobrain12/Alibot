package com.alibot.domain;

public enum MasterStatus {
    ACTIVE,
    DAY_OFF,
    VACATION,
    DISABLED;

    /** Мастеру со статусом VACATION/DISABLED нельзя случайно назначить новый заказ (ТЗ п.8.1). */
    public boolean isAssignable() {
        return this == ACTIVE || this == DAY_OFF;
    }
}
