package com.alibot.service.exception;

import com.alibot.domain.OrderStatus;

/** ТЗ п.15 — попытка невалидного перехода статуса заказа. */
public class InvalidTransitionException extends RuntimeException {
    public InvalidTransitionException(OrderStatus from, OrderStatus to) {
        super("Переход из %s в %s недопустим".formatted(from, to));
    }

    public InvalidTransitionException(String message) {
        super(message);
    }
}
