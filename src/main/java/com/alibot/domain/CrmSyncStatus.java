package com.alibot.domain;

public enum CrmSyncStatus {
    PENDING,
    SENT,
    /** Исчерпаны попытки (ТЗ п.87) — виден админу, доступен ручной повторный запуск. */
    FAILED
}
