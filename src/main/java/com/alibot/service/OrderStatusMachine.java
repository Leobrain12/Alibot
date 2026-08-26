package com.alibot.service;

import com.alibot.domain.OrderStatus;
import com.alibot.service.exception.InvalidTransitionException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * ТЗ п.15 — разрешённые переходы статуса заказа. Это единственное место во всём приложении,
 * которое знает, какой переход валиден. И бот (OrderService.transition, вызываемый из
 * bot/handlers/*), и REST API (OrderController -> OrderService.transition) проходят через
 * этот же класс — ни один транспортный слой не дублирует и не обходит эту таблицу.
 */
@Component
public class OrderStatusMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        allow(OrderStatus.NEW, OrderStatus.ASSIGNED, OrderStatus.CANCELLED);

        allow(OrderStatus.ASSIGNED, OrderStatus.ACCEPTED, OrderStatus.MASTER_DECLINED, OrderStatus.CANCELLED);

        // Отказ мастера -> админ переназначает -> заказ снова ASSIGNED (ТЗ п.22.1).
        allow(OrderStatus.MASTER_DECLINED, OrderStatus.ASSIGNED, OrderStatus.CANCELLED);

        allow(OrderStatus.ACCEPTED, OrderStatus.ON_THE_WAY, OrderStatus.RESCHEDULED,
                OrderStatus.NO_CONTACT, OrderStatus.CANCELLED);

        allow(OrderStatus.RESCHEDULED, OrderStatus.ACCEPTED, OrderStatus.ASSIGNED,
                OrderStatus.ON_THE_WAY, OrderStatus.CANCELLED);

        allow(OrderStatus.NO_CONTACT, OrderStatus.ACCEPTED, OrderStatus.CUSTOMER_CANCELLED,
                OrderStatus.CANCELLED);

        allow(OrderStatus.ON_THE_WAY, OrderStatus.ARRIVED, OrderStatus.RESCHEDULED, OrderStatus.CANCELLED);

        allow(OrderStatus.ARRIVED, OrderStatus.DIAGNOSTICS, OrderStatus.CANCELLED);

        allow(OrderStatus.DIAGNOSTICS, OrderStatus.PRICE_APPROVAL, OrderStatus.WAITING_PART,
                OrderStatus.UNREPAIRABLE, OrderStatus.CUSTOMER_CANCELLED, OrderStatus.CANCELLED);

        allow(OrderStatus.PRICE_APPROVAL, OrderStatus.IN_PROGRESS, OrderStatus.CUSTOMER_CANCELLED,
                OrderStatus.CANCELLED);

        allow(OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED, OrderStatus.WAITING_PART,
                OrderStatus.CUSTOMER_CANCELLED, OrderStatus.CANCELLED);

        // Деталь пришла -> новый визит начинается с выезда (ТЗ п.32/34).
        allow(OrderStatus.WAITING_PART, OrderStatus.ON_THE_WAY, OrderStatus.ARRIVED,
                OrderStatus.CANCELLED);

        allow(OrderStatus.COMPLETED, OrderStatus.PAID, OrderStatus.WARRANTY_RETURN);

        allow(OrderStatus.PAID, OrderStatus.WARRANTY_RETURN);

        // Гарантийный возврат идёт по тому же операционному циклу с начала выезда.
        allow(OrderStatus.WARRANTY_RETURN, OrderStatus.ASSIGNED, OrderStatus.ON_THE_WAY, OrderStatus.CANCELLED);
    }

    private static void allow(OrderStatus from, OrderStatus... to) {
        TRANSITIONS.put(from, EnumSet.copyOf(Set.of(to)));
    }

    public boolean isAllowed(OrderStatus from, OrderStatus to) {
        Set<OrderStatus> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    public void assertTransitionAllowed(OrderStatus from, OrderStatus to) {
        if (!isAllowed(from, to)) {
            throw new InvalidTransitionException(from, to);
        }
    }

    public Set<OrderStatus> allowedFrom(OrderStatus from) {
        return TRANSITIONS.getOrDefault(from, Set.of());
    }
}
