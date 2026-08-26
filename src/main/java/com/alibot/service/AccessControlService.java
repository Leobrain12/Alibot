package com.alibot.service;

import com.alibot.domain.Order;
import com.alibot.service.exception.ForbiddenException;
import org.springframework.stereotype.Service;

/**
 * ТЗ п.91/92 — единая точка проверки прав. И бот, и REST API, и внутренний API проходят через
 * эти же методы, поэтому ни один транспортный слой не может случайно "забыть" проверку доступа.
 */
@Service
public class AccessControlService {

    /** MASTER видит только назначенные ему заказы (ТЗ п.5.3, DoD-Security). ADMIN/SUPERADMIN видят все. */
    public void assertCanView(AuthenticatedActor actor, Order order) {
        if (actor.isAdmin()) {
            return;
        }
        if (actor.isMaster() && order.getMaster() != null && order.getMaster().getId().equals(actor.masterId())) {
            return;
        }
        throw new ForbiddenException("Заказ #%d недоступен пользователю %s".formatted(order.getNumber(), actor.userId()));
    }

    /** Создание/назначение/перенос/отмена — только ADMIN/SUPERADMIN (ТЗ п.5.2). */
    public void assertIsAdmin(AuthenticatedActor actor) {
        if (!actor.isAdmin()) {
            throw new ForbiddenException("Действие доступно только администратору");
        }
    }

    public void assertIsSuperAdmin(AuthenticatedActor actor) {
        if (!actor.isSuperAdmin()) {
            throw new ForbiddenException("Действие доступно только суперадминистратору");
        }
    }

    /** Действия мастера по заказу (принять/выехал/диагностика/отчёт и т.п.) — только назначенный мастер. */
    public void assertIsAssignedMaster(AuthenticatedActor actor, Order order) {
        if (actor.isAdmin()) {
            return;
        }
        if (!actor.isMaster() || order.getMaster() == null || !order.getMaster().getId().equals(actor.masterId())) {
            throw new ForbiddenException("Заказ #%d не назначен этому мастеру".formatted(order.getNumber()));
        }
    }
}
