package com.alibot.service;

import com.alibot.domain.CommissionType;
import com.alibot.domain.Master;
import com.alibot.domain.Order;
import com.alibot.domain.OrderStatus;
import com.alibot.domain.OrderStatusHistory;
import com.alibot.domain.WorkReport;
import com.alibot.repository.OrderRepository;
import com.alibot.repository.OrderStatusHistoryRepository;
import com.alibot.repository.WorkReportRepository;
import com.alibot.service.dto.WorkReportCommand;
import com.alibot.service.exception.ValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ТЗ п.39-46/56-58 — обязательный отчёт мастера. COMPLETED нельзя установить без него (DoD).
 * final_price всегда считается backend'ом (labor + parts) — поэтому в отличие от ТЗ п.57
 * (которое допускает мастеру ввести другую итоговую сумму и тогда показать warning) здесь
 * расхождение просто невозможно: сервер не принимает произвольный final_price от клиента.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class WorkReportService {

    private final WorkReportRepository workReportRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final OrderStatusMachine statusMachine;
    private final AccessControlService accessControl;
    private final NotificationGateway notifications;
    private final OrderService orderService;
    private final AuditLogService auditLog;
    private final CrmSyncService crmSync;

    public WorkReport submit(UUID orderId, WorkReportCommand cmd, AuthenticatedActor actor) {
        Order order = orderService.findOrThrow(orderId);
        accessControl.assertIsAssignedMaster(actor, order);
        statusMachine.assertTransitionAllowed(order.getStatus(), OrderStatus.COMPLETED);

        if (cmd.workDescription() == null || cmd.workDescription().isBlank()) {
            throw new ValidationException("Опишите, что было сделано");
        }
        FieldValidation.requireNonNegative(cmd.laborPrice(), "Стоимость работы");
        FieldValidation.requireNonNegative(cmd.partsSellPrice(), "Цена запчастей клиенту");
        FieldValidation.requireNonNegative(cmd.partsCost(), "Себестоимость запчастей");
        FieldValidation.requireNonNegative(cmd.masterPayout(), "Выплата мастеру");
        BigDecimal labor = nvl(cmd.laborPrice());
        BigDecimal parts = nvl(cmd.partsSellPrice());
        BigDecimal finalPrice = labor.add(parts);
        BigDecimal payout = resolvePayout(order, cmd, finalPrice);

        WorkReport report = WorkReport.builder()
                .orderId(order.getId())
                .masterId(order.getMaster().getId())
                .workDescription(cmd.workDescription())
                .laborPrice(labor)
                .partsSellPrice(parts)
                .partsCost(cmd.partsCost())
                .finalPrice(finalPrice)
                .masterPayout(payout)
                .comment(cmd.comment())
                .confirmedAt(Instant.now())
                .build();
        report = workReportRepository.save(report);

        OrderStatus old = order.getStatus();
        order.setLaborPrice(labor);
        order.setPartsSellPrice(parts);
        order.setPartsCost(cmd.partsCost());
        order.setFinalPrice(finalPrice);
        order.setMasterPayout(payout);
        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(Instant.now());
        orderRepository.save(order);

        historyRepository.save(OrderStatusHistory.builder()
                .orderId(order.getId())
                .oldStatus(old)
                .newStatus(OrderStatus.COMPLETED)
                .changedByUserId(actor.userId())
                .comment("WorkReport #" + report.getId())
                .build());
        auditLog.record("STATUS_CHANGED", "ORDER", order.getId(), actor.userId(), old.name(), OrderStatus.COMPLETED.name());
        auditLog.record("WORK_REPORT_CREATED", "ORDER", order.getId(), actor.userId(), null, finalPrice.toPlainString());
        // Как и PaymentService — этот путь пишет OrderStatusHistory напрямую, в обход
        // OrderService.recordHistory (единой точки, из которой обычно кормится очередь CRM),
        // поэтому хук здесь нужен явно. Без него завершение заказа — самый важный переход из
        // всех — никогда не долетало бы до внешней CRM.
        crmSync.enqueue(order, "STATUS_CHANGED");

        notifications.orderCompletedNotifyAdmin(order);
        return report;
    }

    private BigDecimal resolvePayout(Order order, WorkReportCommand cmd, BigDecimal finalPrice) {
        Master master = order.getMaster();
        if (master.getCommissionType() == CommissionType.MANUAL) {
            // Мастер видел вопрос про сумму выплаты в визарде только если на момент того шага
            // (WorkReportWizard.advancePastCost) у мастера ещё была стоять MANUAL — тип комиссии
            // мог смениться администратором позже, за время заполнения отчёта. Раньше при null
            // здесь молча подставлялся 0 — платёж на завершённом, оплаченном заказе получался
            // тихо неверным без единого предупреждения. Явная ошибка лучше тихого нуля: мастер
            // увидит её и перезапустит отчёт (сумма уже спросится, т.к. тип комиссии актуален).
            if (cmd.masterPayout() == null) {
                throw new ValidationException(
                        "Тип выплаты мастера изменился на «вручную» уже после начала отчёта — начните отчёт заново, чтобы указать сумму выплаты");
            }
            return cmd.masterPayout();
        }
        BigDecimal value = master.getCommissionValue() == null ? BigDecimal.ZERO : master.getCommissionValue();
        return switch (master.getCommissionType()) {
            case FIXED -> value;
            case PERCENT -> finalPrice.multiply(value)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case MANUAL -> nvl(cmd.masterPayout());
        };
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
