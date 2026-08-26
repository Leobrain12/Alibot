package com.alibot.service;

import com.alibot.service.exception.ValidationException;
import java.math.BigDecimal;
import java.time.LocalTime;

/** Общие проверки полей, используемые в нескольких сервисах (OrderService, WorkReportService,
 *  UserManagementService и т.п.) — чтобы одна и та же проверка ("цена не может быть
 *  отрицательной", "время начала раньше времени конца") не дублировалась в каждом отдельно. */
final class FieldValidation {

    private FieldValidation() {
    }

    static void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(message);
        }
    }

    /** null допустим (поле необязательно) — но если значение задано, отрицательным быть не может. */
    static void requireNonNegative(BigDecimal value, String fieldName) {
        if (value != null && value.signum() < 0) {
            throw new ValidationException(fieldName + " не может быть отрицательным");
        }
    }

    static void requireValidSlot(LocalTime from, LocalTime to) {
        if (from == null || to == null) {
            throw new ValidationException("Время визита «с» и «до» обязательны");
        }
        if (!from.isBefore(to)) {
            throw new ValidationException("Время «с» должно быть раньше времени «до»");
        }
    }
}
