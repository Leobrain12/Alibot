package com.alibot.service.exception;

/** ТЗ п.91/92 — нарушение прав доступа (роль или object-level ownership). */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
