package com.alibot.service;

import com.alibot.domain.Order;
import com.alibot.domain.OrderStatus;
import com.alibot.domain.OrderVisit;
import com.alibot.repository.OrderVisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ТЗ п.33/34 — повторное посещение в рамках одного заказа (например, деталь пришла — новый выезд),
 * не создавая независимый новый Order. Тонкая обвязка вокруг OrderService: реальный переход
 * статуса всё так же идёт только через OrderStatusMachine.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class VisitService {

    private final OrderVisitRepository visitRepository;
    private final OrderService orderService;

    public OrderVisit recordCompletedVisit(Order order, String label) {
        int nextNumber = (int) visitRepository.countByOrderId(order.getId()) + 1;
        OrderVisit visit = OrderVisit.builder()
                .orderId(order.getId())
                .visitNumber(nextNumber)
                .visitDate(order.getVisitDate())
                .timeFrom(order.getTimeFrom())
                .timeTo(order.getTimeTo())
                .masterId(order.getMaster() != null ? order.getMaster().getId() : null)
                .status(order.getStatus())
                .reason(label)
                .build();
        return visitRepository.save(visit);
    }

    /** Деталь пришла (или гарантийный возврат) — начинается новый выезд по тому же заказу. */
    public Order startRepeatVisit(java.util.UUID orderId, AuthenticatedActor actor) {
        Order order = orderService.findOrThrow(orderId);
        recordCompletedVisit(order, "Визит #" + ((int) visitRepository.countByOrderId(orderId) + 1) + " начат");
        return orderService.transitionSimple(orderId, OrderStatus.ON_THE_WAY, actor, "Повторный визит");
    }
}
