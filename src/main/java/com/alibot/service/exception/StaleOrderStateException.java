package com.alibot.service.exception;

/** ТЗ п.112 — конкурентное изменение заказа (например, старый мастер жмёт "Принять" после того,
 *  как админ уже переназначил заказ другому). */
public class StaleOrderStateException extends RuntimeException {
    public StaleOrderStateException(String message) {
        super(message);
    }
}
