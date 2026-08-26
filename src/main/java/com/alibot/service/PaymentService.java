package com.alibot.service;

import com.alibot.domain.Order;
import com.alibot.domain.OrderStatus;
import com.alibot.domain.OrderStatusHistory;
import com.alibot.domain.Payment;
import com.alibot.repository.OrderRepository;
import com.alibot.repository.OrderStatusHistoryRepository;
import com.alibot.repository.PaymentRepository;
import com.alibot.service.dto.PaymentCommand;
import com.alibot.service.exception.ValidationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ТЗ п.60-63 — полная/частичная оплата, история платежей. */
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final AccessControlService accessControl;
    private final NotificationGateway notifications;
    private final OrderService orderService;
    private final AuditLogService auditLog;
    private final CrmSyncService crmSync;

    public Payment registerPayment(UUID orderId, PaymentCommand cmd, AuthenticatedActor actor) {
        accessControl.assertIsAdmin(actor);
        Order order = orderService.findOrThrow(orderId);
        if (order.getStatus() != OrderStatus.COMPLETED && order.getStatus() != OrderStatus.PAID) {
            throw new ValidationException("Оплату можно зарегистрировать только для завершённого заказа");
        }
        if (cmd.amount() == null || cmd.amount().signum() <= 0) {
            throw new ValidationException("Сумма оплаты должна быть положительной");
        }
        // Ловим опечатку в сумме (лишний ноль и т.п.) явной ошибкой вместо того, чтобы тихо
        // принять переплату — payFull() сюда не попадает, он сам считает amount = amountDue().
        BigDecimal due = order.amountDue();
        if (cmd.amount().compareTo(due) > 0) {
            throw new ValidationException("Сумма оплаты (%s) больше остатка долга (%s)"
                    .formatted(cmd.amount().toPlainString(), due.toPlainString()));
        }

        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(cmd.amount())
                .paymentType(cmd.paymentType())
                .receivedBy(actor.userId())
                .build();
        payment = paymentRepository.save(payment);
        auditLog.record("PAYMENT_CHANGED", "ORDER", order.getId(), actor.userId(),
                order.getAmountPaid() == null ? null : order.getAmountPaid().toPlainString(),
                cmd.amount().toPlainString());
        crmSync.enqueue(order, "PAYMENT_REGISTERED");

        BigDecimal totalPaid = (order.getAmountPaid() == null ? BigDecimal.ZERO : order.getAmountPaid())
                .add(cmd.amount());
        order.setAmountPaid(totalPaid);

        OrderStatus old = order.getStatus();
        boolean fullyPaid = order.getFinalPrice() != null && totalPaid.compareTo(order.getFinalPrice()) >= 0;
        if (fullyPaid) {
            order.setStatus(OrderStatus.PAID);
        }
        orderRepository.save(order);

        if (fullyPaid && old != OrderStatus.PAID) {
            historyRepository.save(OrderStatusHistory.builder()
                    .orderId(order.getId())
                    .oldStatus(old)
                    .newStatus(OrderStatus.PAID)
                    .changedByUserId(actor.userId())
                    .comment("Оплата #" + payment.getId())
                    .build());
            auditLog.record("STATUS_CHANGED", "ORDER", order.getId(), actor.userId(), old.name(), OrderStatus.PAID.name());
            crmSync.enqueue(order, "STATUS_CHANGED");
            notifications.orderPaidNotifyAdmin(order);
        }
        return payment;
    }

    /** ТЗ п.61 — оплачено полностью. */
    public Payment payFull(UUID orderId, AuthenticatedActor actor) {
        Order order = orderService.findOrThrow(orderId);
        BigDecimal due = order.amountDue();
        if (due.signum() <= 0 && order.getFinalPrice() != null) {
            due = order.getFinalPrice().subtract(order.getAmountPaid() == null ? BigDecimal.ZERO : order.getAmountPaid());
        }
        return registerPayment(orderId, new PaymentCommand(due, "FULL"), actor);
    }

    @Transactional(readOnly = true)
    public List<Payment> history(UUID orderId, AuthenticatedActor actor) {
        Order order = orderService.getById(orderId, actor);
        return paymentRepository.findByOrderIdOrderByCreatedAtDesc(order.getId());
    }
}
