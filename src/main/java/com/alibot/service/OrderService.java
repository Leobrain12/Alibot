package com.alibot.service;

import com.alibot.domain.Master;
import com.alibot.domain.Order;
import com.alibot.domain.OrderStatus;
import com.alibot.domain.OrderStatusHistory;
import com.alibot.repository.MasterRepository;
import com.alibot.repository.OrderRepository;
import com.alibot.repository.OrderStatusHistoryRepository;
import com.alibot.service.dto.CreateOrderCommand;
import com.alibot.service.dto.PriceApprovalCommand;
import com.alibot.service.dto.RescheduleCommand;
import com.alibot.service.dto.WaitingPartCommand;
import com.alibot.service.dto.WarrantyCommand;
import com.alibot.service.exception.NotFoundException;
import com.alibot.service.exception.ValidationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Единственное место, где принимаются решения о заказе — и bot/handlers/*, и api/controller/*
 * вызывают только эти методы. Ни один из них не содержит собственных правил валидности
 * переходов или прав доступа (ТЗ: "бизнес-логика не должна находиться исключительно внутри
 * Telegram handlers").
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final MasterRepository masterRepository;
    private final OrderStatusMachine statusMachine;
    private final AccessControlService accessControl;
    private final NotificationGateway notifications;
    private final AuditLogService auditLog;
    private final CrmSyncService crmSync;

    /** Единственные target'ы, легитимные для generic-перехода без доп. полей — см. transitionSimple. */
    private static final java.util.Set<OrderStatus> SIMPLE_TRANSITION_TARGETS =
            java.util.EnumSet.of(OrderStatus.ACCEPTED, OrderStatus.ON_THE_WAY);

    // --- Чтение ---

    @Transactional(readOnly = true)
    public Order getById(UUID id, AuthenticatedActor actor) {
        Order order = findOrThrow(id);
        accessControl.assertCanView(actor, order);
        return order;
    }

    @Transactional(readOnly = true)
    public Order getByNumber(Long number, AuthenticatedActor actor) {
        Order order = orderRepository.findByNumber(number)
                .orElseThrow(() -> new NotFoundException("Заказ #%d не найден".formatted(number)));
        accessControl.assertCanView(actor, order);
        return order;
    }

    @Transactional(readOnly = true)
    public List<Order> listActive(AuthenticatedActor actor) {
        List<OrderStatus> active = List.of(OrderStatus.NEW, OrderStatus.ASSIGNED, OrderStatus.ACCEPTED,
                OrderStatus.ON_THE_WAY, OrderStatus.ARRIVED, OrderStatus.DIAGNOSTICS, OrderStatus.PRICE_APPROVAL,
                OrderStatus.IN_PROGRESS, OrderStatus.WAITING_PART, OrderStatus.RESCHEDULED,
                OrderStatus.MASTER_DECLINED, OrderStatus.NO_CONTACT, OrderStatus.COMPLETED,
                OrderStatus.WARRANTY_RETURN);
        if (actor.isAdmin()) {
            return orderRepository.findByStatusInOrderByCreatedAtDesc(active);
        }
        return orderRepository.findByMasterIdAndStatusInOrderByVisitDateAsc(actor.masterId(), active);
    }

    @Transactional(readOnly = true)
    public List<Order> listHistory(AuthenticatedActor actor) {
        List<OrderStatus> historyStatuses = List.of(OrderStatus.PAID, OrderStatus.CANCELLED,
                OrderStatus.CUSTOMER_CANCELLED, OrderStatus.UNREPAIRABLE);
        if (actor.isAdmin()) {
            return orderRepository.findByStatusInOrderByCreatedAtDesc(historyStatuses);
        }
        return orderRepository.findByMasterIdAndStatusInOrderByVisitDateAsc(actor.masterId(), historyStatuses);
    }

    /** ТЗ п.71 — заказы, требующие назначения. */
    @Transactional(readOnly = true)
    public List<Order> listUnassigned(AuthenticatedActor actor) {
        accessControl.assertIsAdmin(actor);
        return orderRepository.findByStatusInOrderByCreatedAtDesc(
                List.of(OrderStatus.NEW, OrderStatus.MASTER_DECLINED));
    }

    @Transactional(readOnly = true)
    public List<Order> search(String query, AuthenticatedActor actor) {
        return search(query, null, null, actor);
    }

    /** ТЗ п.72 — поиск по номеру/телефону/имени/адресу, дополнительно сужаемый по мастеру и дате визита. */
    @Transactional(readOnly = true)
    public List<Order> search(String query, UUID masterId, java.time.LocalDate visitDate, AuthenticatedActor actor) {
        List<Order> found = (query == null || query.isBlank())
                ? orderRepository.findAllByOrderByCreatedAtDesc()
                : orderRepository.search(query);
        if (masterId != null) {
            found = found.stream().filter(o -> o.getMaster() != null && o.getMaster().getId().equals(masterId)).toList();
        }
        if (visitDate != null) {
            found = found.stream().filter(o -> visitDate.equals(o.getVisitDate())).toList();
        }
        if (actor.isAdmin()) {
            return found;
        }
        return found.stream().filter(o -> o.getMaster() != null && o.getMaster().getId().equals(actor.masterId())).toList();
    }

    /** ТЗ п.80 — выгрузка заказов за период (для CSV/XLSX-экспорта), только ADMIN/SUPERADMIN. */
    @Transactional(readOnly = true)
    public List<Order> exportData(Instant from, Instant to, OrderStatus status, AuthenticatedActor actor) {
        accessControl.assertIsAdmin(actor);
        List<Order> found = orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to);
        if (status != null) {
            found = found.stream().filter(o -> o.getStatus() == status).toList();
        }
        return found;
    }

    /** ТЗ Figma #11 — история статусов конкретного заказа (Timeline на экране заявки). */
    @Transactional(readOnly = true)
    public List<OrderStatusHistory> getHistory(UUID orderId, AuthenticatedActor actor) {
        Order order = getById(orderId, actor); // проверка доступа тем же путём, что и обычный просмотр заказа
        return historyRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());
    }

    // --- Создание и назначение ---

    public Order create(CreateOrderCommand cmd, AuthenticatedActor actor) {
        accessControl.assertIsAdmin(actor);
        validateCreate(cmd);

        Master master = null;
        if (cmd.masterId() != null) {
            master = requireAssignableMaster(cmd.masterId());
        }

        Order order = Order.builder()
                .number(orderRepository.nextOrderNumber())
                .leadId(cmd.leadId())
                .crmId(cmd.crmId())
                .source(cmd.source())
                .customerName(cmd.customerName())
                .customerPhone(cmd.customerPhone())
                .applianceType(cmd.applianceType())
                .brand(cmd.brand())
                .model(cmd.model())
                .symptom(cmd.symptom())
                .description(cmd.description())
                .address(cmd.address())
                .visitDate(cmd.visitDate())
                .timeFrom(cmd.timeFrom())
                .timeTo(cmd.timeTo())
                .master(master)
                .status(master != null ? OrderStatus.ASSIGNED : OrderStatus.NEW)
                .adminComment(cmd.adminComment())
                .createdBy(actor.userId())
                .build();

        order = orderRepository.save(order);
        recordHistory(order, null, order.getStatus(), actor, "Заказ создан");
        auditLog.record("ORDER_CREATED", "ORDER", order.getId(), actor.userId(), null,
                "#%d, %s".formatted(order.getNumber(), order.getApplianceType()));

        if (master != null) {
            notifications.masterAssigned(order);
        }
        return order;
    }

    /** ТЗ п.17.2 — создание заказа через внешний API (сайт/CRM) идёт через тот же метод create(),
     *  разница только в actor (AuthenticatedActor.system при вызове от внешней системы). */

    public Order assign(UUID orderId, UUID masterId, AuthenticatedActor actor) {
        accessControl.assertIsAdmin(actor);
        Order order = findOrThrow(orderId);
        statusMachine.assertTransitionAllowed(order.getStatus(), OrderStatus.ASSIGNED);

        Master master = requireAssignableMaster(masterId);
        OrderStatus old = order.getStatus();
        order.setMaster(master);
        order.setStatus(OrderStatus.ASSIGNED);
        order.setAcceptTimeoutNotifiedAt(null); // новое назначение — таймер подтверждения начинается заново
        orderRepository.save(order);
        recordHistory(order, old, OrderStatus.ASSIGNED, actor, "Назначен мастер " + master.getName());
        auditLog.record("MASTER_ASSIGNED", "ORDER", order.getId(), actor.userId(), null, master.getName());
        notifications.masterAssigned(order);
        return order;
    }

    /** ТЗ п.114 — смена мастера: старому уходит уведомление о передаче, новому — обычное назначение. */
    public Order changeMaster(UUID orderId, UUID newMasterId, AuthenticatedActor actor) {
        accessControl.assertIsAdmin(actor);
        Order order = findOrThrow(orderId);
        // isTerminal() не включает COMPLETED (заказ ещё может стать PAID или уйти в гарантию —
        // OrderStatusMachine.java:56) — но у COMPLETED уже есть финальный WorkReport
        // (finalPrice/masterPayout/completedAt), и OrderStatusMachine нигде не разрешает
        // COMPLETED -> ASSIGNED. Раньше changeMaster это игнорировал и молча реоткрывал
        // завершённый заказ, стирая его финальное состояние без единой проверки.
        if (order.getStatus().isTerminal() || order.getStatus() == OrderStatus.COMPLETED) {
            throw new ValidationException("Нельзя сменить мастера у завершённого заказа");
        }
        Master oldMaster = order.getMaster();
        Master newMaster = requireAssignableMaster(newMasterId);

        OrderStatus old = order.getStatus();
        order.setMaster(newMaster);
        order.setStatus(OrderStatus.ASSIGNED);
        order.setAcceptTimeoutNotifiedAt(null);
        orderRepository.save(order);
        recordHistory(order, old, OrderStatus.ASSIGNED, actor, "Мастер изменён на " + newMaster.getName());
        auditLog.record("MASTER_CHANGED", "ORDER", order.getId(), actor.userId(),
                oldMaster != null ? oldMaster.getName() : null, newMaster.getName());

        if (oldMaster != null && !oldMaster.getId().equals(newMaster.getId())) {
            notifications.masterTransferredNotifyOldMaster(order);
        }
        notifications.masterAssigned(order);
        return order;
    }

    // --- Операционный флоу мастера ---

    public Order acceptByMaster(UUID orderId, AuthenticatedActor actor) {
        Order order = findOrThrow(orderId);
        accessControl.assertIsAssignedMaster(actor, order);
        statusMachine.assertTransitionAllowed(order.getStatus(), OrderStatus.ACCEPTED);

        OrderStatus old = order.getStatus();
        order.setStatus(OrderStatus.ACCEPTED);
        order.setAcceptedAt(Instant.now());
        orderRepository.save(order);
        recordHistory(order, old, OrderStatus.ACCEPTED, actor, null);
        notifications.masterAcceptedNotifyAdmin(order);
        return order;
    }

    /** ТЗ п.22 — отказ мастера, причина обязательна. */
    public Order declineByMaster(UUID orderId, String reason, AuthenticatedActor actor) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("Причина отказа обязательна");
        }
        Order order = findOrThrow(orderId);
        accessControl.assertIsAssignedMaster(actor, order);
        statusMachine.assertTransitionAllowed(order.getStatus(), OrderStatus.MASTER_DECLINED);

        OrderStatus old = order.getStatus();
        order.setStatus(OrderStatus.MASTER_DECLINED);
        order.setCancelReason(reason);
        orderRepository.save(order);
        recordHistory(order, old, OrderStatus.MASTER_DECLINED, actor, reason);
        notifications.masterDeclinedNotifyAdmin(order, reason);
        return order;
    }

    public Order markOnTheWay(UUID orderId, AuthenticatedActor actor) {
        Order order = findOrThrow(orderId);
        accessControl.assertIsAssignedMaster(actor, order);
        statusMachine.assertTransitionAllowed(order.getStatus(), OrderStatus.ON_THE_WAY);

        OrderStatus old = order.getStatus();
        order.setStatus(OrderStatus.ON_THE_WAY);
        order.setOnTheWayAt(Instant.now());
        orderRepository.save(order);
        recordHistory(order, old, OrderStatus.ON_THE_WAY, actor, null);
        return order;
    }

    public Order markArrived(UUID orderId, AuthenticatedActor actor) {
        Order order = findOrThrow(orderId);
        accessControl.assertIsAssignedMaster(actor, order);
        statusMachine.assertTransitionAllowed(order.getStatus(), OrderStatus.ARRIVED);

        OrderStatus old = order.getStatus();
        order.setStatus(OrderStatus.ARRIVED);
        order.setArrivedAt(Instant.now());
        orderRepository.save(order);
        recordHistory(order, old, OrderStatus.ARRIVED, actor, null);
        return order;
    }

    public Order startDiagnostics(UUID orderId, AuthenticatedActor actor) {
        Order order = findOrThrow(orderId);
        accessControl.assertIsAssignedMaster(actor, order);
        statusMachine.assertTransitionAllowed(order.getStatus(), OrderStatus.DIAGNOSTICS);

        OrderStatus old = order.getStatus();
        order.setStatus(OrderStatus.DIAGNOSTICS);
        orderRepository.save(order);
        recordHistory(order, old, OrderStatus.DIAGNOSTICS, actor, null);
        return order;
    }

    /** ТЗ п.28/29 — "Можно ремонтировать": фиксируем причину/работы/цену, входим в согласование. */
    public Order startPriceApproval(UUID orderId, PriceApprovalCommand cmd, AuthenticatedActor actor) {
        FieldValidation.requireNonBlank(cmd.failureReason(), "Причина неисправности обязательна");
        FieldValidation.requireNonNegative(cmd.laborPrice(), "Стоимость работы");
        FieldValidation.requireNonNegative(cmd.partsSellPrice(), "Цена запчастей");
        Order order = findOrThrow(orderId);
        accessControl.assertIsAssignedMaster(actor, order);
        statusMachine.assertTransitionAllowed(order.getStatus(), OrderStatus.PRICE_APPROVAL);

        BigDecimal labor = nvl(cmd.laborPrice());
        BigDecimal parts = nvl(cmd.partsSellPrice());

        OrderStatus old = order.getStatus();
        order.setMasterComment(cmd.failureReason());
        order.setLaborPrice(labor);
        order.setPartsSellPrice(parts);
        order.setEstimatedPrice(labor.add(parts));
        order.setStatus(OrderStatus.PRICE_APPROVAL);
        orderRepository.save(order);
        recordHistory(order, old, OrderStatus.PRICE_APPROVAL, actor, cmd.workNeeded());
        auditLog.record("PRICE_CHANGED", "ORDER", order.getId(), actor.userId(), null,
                order.getEstimatedPrice().toPlainString());
        return order;
    }

    /** ТЗ п.30 — клиент согласовал стоимость. */
    public Order approvePriceByCustomer(UUID orderId, AuthenticatedActor actor) {
        Order order = findOrThrow(orderId);
        accessControl.assertIsAssignedMaster(actor, order);
        statusMachine.assertTransitionAllowed(order.getStatus(), OrderStatus.IN_PROGRESS);

        OrderStatus old = order.getStatus();
        order.setFinalPrice(order.getEstimatedPrice());
        order.setStatus(OrderStatus.IN_PROGRESS);
        orderRepository.save(order);
        recordHistory(order, old, OrderStatus.IN_PROGRESS, actor, "Клиент согласовал стоимость");
        return order;
    }

    /** ТЗ п.31 — клиент отказался (на этапе согласования цены либо ремонта), причина обязательна. */
    public Order declineByCustomer(UUID orderId, String reason, AuthenticatedActor actor) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("Причина отказа клиента обязательна");
        }
        Order order = findOrThrow(orderId);
        accessControl.assertIsAssignedMaster(actor, order);
        statusMachine.assertTransitionAllowed(order.getStatus(), OrderStatus.CUSTOMER_CANCELLED);

        OrderStatus old = order.getStatus();
        order.setStatus(OrderStatus.CUSTOMER_CANCELLED);
        order.setCancelReason(reason);
        orderRepository.save(order);
        recordHistory(order, old, OrderStatus.CUSTOMER_CANCELLED, actor, reason);
        return order;
    }

    /** ТЗ п.27/28 — "Ремонт нецелесообразен". */
    public Order markUnrepairable(UUID orderId, String reason, AuthenticatedActor actor) {
        Order order = findOrThrow(orderId);
        accessControl.assertIsAssignedMaster(actor, order);
        statusMachine.assertTransitionAllowed(order.getStatus(), OrderStatus.UNREPAIRABLE);

        OrderStatus old = order.getStatus();
        order.setStatus(OrderStatus.UNREPAIRABLE);
        order.setCancelReason(reason);
        orderRepository.save(order);
        recordHistory(order, old, OrderStatus.UNREPAIRABLE, actor, reason);
        return order;
    }

    /** ТЗ п.32 — нужна деталь. */
    public Order markWaitingPart(UUID orderId, WaitingPartCommand cmd, AuthenticatedActor actor) {
        FieldValidation.requireNonBlank(cmd.partName(), "Название детали обязательно");
        FieldValidation.requireNonNegative(cmd.estimatedPurchasePrice(), "Ориентировочная закупочная цена");
        Order order = findOrThrow(orderId);
        accessControl.assertIsAssignedMaster(actor, order);
        statusMachine.assertTransitionAllowed(order.getStatus(), OrderStatus.WAITING_PART);

        OrderStatus old = order.getStatus();
        order.setPartName(cmd.partName());
        order.setPartNumber(cmd.partNumber());
        order.setPartEstimatedCost(cmd.estimatedPurchasePrice());
        order.setMasterComment(cmd.comment());
        order.setStatus(OrderStatus.WAITING_PART);
        orderRepository.save(order);
        recordHistory(order, old, OrderStatus.WAITING_PART, actor, cmd.partName());
        notifications.partNeededNotifyAdmin(order, cmd.partName(), cmd.comment());
        return order;
    }

    /** ТЗ п.35 — перенос: обязательны новая дата/слот/причина. Доступен мастеру и админу. */
    public Order reschedule(UUID orderId, RescheduleCommand cmd, AuthenticatedActor actor) {
        if (cmd.reason() == null || cmd.reason().isBlank() || cmd.newDate() == null) {
            throw new ValidationException("Для переноса обязательны дата, слот и причина");
        }
        FieldValidation.requireValidSlot(cmd.newFrom(), cmd.newTo());
        Order order = findOrThrow(orderId);
        accessControl.assertIsAssignedMaster(actor, order);
        statusMachine.assertTransitionAllowed(order.getStatus(), OrderStatus.RESCHEDULED);

        OrderStatus old = order.getStatus();
        order.setVisitDate(cmd.newDate());
        order.setTimeFrom(cmd.newFrom());
        order.setTimeTo(cmd.newTo());
        order.setCancelReason(cmd.reason());
        order.setStatus(OrderStatus.RESCHEDULED);
        order.setReminderSentAt(null); // время визита изменилось — напомнить нужно будет заново
        orderRepository.save(order);
        recordHistory(order, old, OrderStatus.RESCHEDULED, actor, cmd.reason());
        notifications.orderChangedNotifyMaster(order, "Заказ перенесён: " + cmd.reason());
        return order;
    }

    /** Общий переход для простых случаев без дополнительных обязательных полей
     *  (например RESCHEDULED -> ACCEPTED/ON_THE_WAY после переноса, NO_CONTACT -> ACCEPTED после дозвона,
     *  WAITING_PART -> ON_THE_WAY на повторный визит, WARRANTY_RETURN -> ON_THE_WAY).
     *  Намеренно НЕ пропускает произвольный target: каждый другой статус либо требует
     *  обязательных полей, которых этот метод не собирает (WAITING_PART/PRICE_APPROVAL — деталь/
     *  цена, MASTER_DECLINED/CANCELLED/UNREPAIRABLE/CUSTOMER_CANCELLED — обязательная причина,
     *  RESCHEDULED — новая дата), либо имеет единственный легитимный путь через другой сервис
     *  (COMPLETED — только через WorkReportService, PAID — только через PaymentService). Раньше
     *  вызов POST /api/v1/orders/{id}/transition с любым из этих targetStatus молча обходил все
     *  эти правила — например переводил заказ в COMPLETED без отчёта о работе и суммы. */
    public Order transitionSimple(UUID orderId, OrderStatus target, AuthenticatedActor actor, String comment) {
        if (!SIMPLE_TRANSITION_TARGETS.contains(target)) {
            throw new ValidationException(
                    "Переход в статус %s требует отдельного эндпоинта с обязательными полями, не общего /transition"
                            .formatted(target));
        }
        Order order = findOrThrow(orderId);
        accessControl.assertIsAssignedMaster(actor, order);
        statusMachine.assertTransitionAllowed(order.getStatus(), target);

        OrderStatus old = order.getStatus();
        order.setStatus(target);
        orderRepository.save(order);
        recordHistory(order, old, target, actor, comment);
        return order;
    }

    /** ТЗ п.38 — админ переводит заказ в NO_CONTACT после нескольких недозвонов. */
    public Order markNoContact(UUID orderId, AuthenticatedActor actor) {
        accessControl.assertIsAdmin(actor);
        Order order = findOrThrow(orderId);
        statusMachine.assertTransitionAllowed(order.getStatus(), OrderStatus.NO_CONTACT);

        OrderStatus old = order.getStatus();
        order.setStatus(OrderStatus.NO_CONTACT);
        orderRepository.save(order);
        recordHistory(order, old, OrderStatus.NO_CONTACT, actor, null);
        return order;
    }

    /** ТЗ п.115 — обычный ADMIN не удаляет заказ физически, только CANCELLED. */
    public Order cancel(UUID orderId, String reason, AuthenticatedActor actor) {
        accessControl.assertIsAdmin(actor);
        Order order = findOrThrow(orderId);
        if (order.getStatus().isTerminal()) {
            throw new ValidationException("Заказ уже в финальном статусе");
        }
        statusMachine.assertTransitionAllowed(order.getStatus(), OrderStatus.CANCELLED);

        OrderStatus old = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(reason);
        orderRepository.save(order);
        recordHistory(order, old, OrderStatus.CANCELLED, actor, reason);
        return order;
    }

    /** ТЗ п.64 — гарантийное обращение: новый заказ, ссылающийся на исходный, исходный помечается WARRANTY_RETURN. */
    public Order createWarrantyOrder(WarrantyCommand cmd, AuthenticatedActor actor) {
        FieldValidation.requireNonBlank(cmd.problem(), "Описание гарантийной проблемы обязательно");
        if (cmd.visitDate() == null) {
            throw new ValidationException("Дата гарантийного визита обязательна");
        }
        FieldValidation.requireValidSlot(cmd.timeFrom(), cmd.timeTo());
        accessControl.assertIsAdmin(actor);
        Order original = findOrThrow(cmd.originalOrderId());
        statusMachine.assertTransitionAllowed(original.getStatus(), OrderStatus.WARRANTY_RETURN);

        OrderStatus originalOld = original.getStatus();
        original.setStatus(OrderStatus.WARRANTY_RETURN);
        orderRepository.save(original);
        recordHistory(original, originalOld, OrderStatus.WARRANTY_RETURN, actor, "Открыто гарантийное обращение");
        notifications.warrantyCreatedNotifyAdmin(original);

        Master master = cmd.masterId() != null ? requireAssignableMaster(cmd.masterId()) : original.getMaster();

        Order warrantyOrder = Order.builder()
                .number(orderRepository.nextOrderNumber())
                .customerName(original.getCustomerName())
                .customerPhone(original.getCustomerPhone())
                .applianceType(original.getApplianceType())
                .brand(original.getBrand())
                .model(original.getModel())
                .symptom(cmd.problem())
                .description("Гарантийное обращение по заказу #" + original.getNumber())
                .address(original.getAddress())
                .visitDate(cmd.visitDate())
                .timeFrom(cmd.timeFrom())
                .timeTo(cmd.timeTo())
                .master(master)
                .status(master != null ? OrderStatus.ASSIGNED : OrderStatus.NEW)
                .adminComment(cmd.comment())
                .warrantyParentOrderId(original.getId())
                .createdBy(actor.userId())
                .build();

        warrantyOrder = orderRepository.save(warrantyOrder);
        recordHistory(warrantyOrder, null, warrantyOrder.getStatus(), actor, "Создан гарантийный визит");
        auditLog.record("WARRANTY_CREATED", "ORDER", warrantyOrder.getId(), actor.userId(),
                null, "по заказу #" + original.getNumber());
        if (master != null) {
            notifications.masterAssigned(warrantyOrder);
        }
        return warrantyOrder;
    }

    /** ТЗ п.88 PATCH /orders/{id} — точечное редактирование некритичных полей заявки. */
    public Order updateDetails(UUID orderId, String adminComment, String description, AuthenticatedActor actor) {
        accessControl.assertIsAdmin(actor);
        Order order = findOrThrow(orderId);
        if (adminComment != null) {
            order.setAdminComment(adminComment);
        }
        if (description != null) {
            order.setDescription(description);
        }
        return orderRepository.save(order);
    }

    // --- Вспомогательное ---

    Order findOrThrow(UUID id) {
        // findByIdWithMaster (не findById) — тянет master через JOIN FETCH, см. OrderRepository.
        return orderRepository.findByIdWithMaster(id)
                .orElseThrow(() -> new NotFoundException("Заказ " + id + " не найден"));
    }

    private Master requireAssignableMaster(UUID masterId) {
        Master master = masterRepository.findByIdWithUser(masterId)
                .orElseThrow(() -> new NotFoundException("Мастер " + masterId + " не найден"));
        if (!master.isAssignable()) {
            throw new ValidationException("Мастер %s недоступен для назначения (статус %s)"
                    .formatted(master.getName(), master.getStatus()));
        }
        return master;
    }

    private void recordHistory(Order order, OrderStatus oldStatus, OrderStatus newStatus,
                                AuthenticatedActor actor, String comment) {
        historyRepository.save(OrderStatusHistory.builder()
                .orderId(order.getId())
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .changedByUserId(actor.userId())
                .comment(comment)
                .build());
        // ТЗ п.95.1 — единая точка для STATUS_CHANGED/ORDER_CANCELLED: recordHistory уже
        // вызывается из каждого метода, меняющего статус, поэтому один хук покрывает их все.
        String action = newStatus == OrderStatus.CANCELLED ? "ORDER_CANCELLED" : "STATUS_CHANGED";
        auditLog.record(action, "ORDER", order.getId(), actor.userId(),
                oldStatus == null ? null : oldStatus.name(), newStatus.name());
        // ТЗ п.85-87 — та же единая точка кормит очередь CRM-синхронизации; CrmSyncService сам
        // решает, писать ли запись (только если app.crm.webhook-url настроен).
        crmSync.enqueue(order, oldStatus == null ? "ORDER_CREATED" : "STATUS_CHANGED");
    }

    private void validateCreate(CreateOrderCommand cmd) {
        if (cmd.customerName() == null || cmd.customerName().isBlank()) {
            throw new ValidationException("Имя клиента обязательно");
        }
        if (cmd.customerPhone() == null || cmd.customerPhone().isBlank()) {
            throw new ValidationException("Телефон клиента обязателен");
        }
        // Раньше эта проверка была только в CreateOrderWizard (бот) — REST API (сайт/CRM/Mini App)
        // мог создать заказ с телефоном из 3 цифр. Единственное место, принимающее решения о
        // заказе, должно быть и единственным местом, проверяющим его поля (см. класс-комментарий).
        if (cmd.customerPhone().replaceAll("\\D", "").length() < 10) {
            throw new ValidationException("Похоже на некорректный номер телефона");
        }
        if (cmd.address() == null || cmd.address().isBlank()) {
            throw new ValidationException("Адрес обязателен");
        }
        if (cmd.applianceType() == null || cmd.applianceType().isBlank()) {
            throw new ValidationException("Тип техники обязателен");
        }
        if (cmd.symptom() == null || cmd.symptom().isBlank()) {
            throw new ValidationException("Проблема обязательна");
        }
        if (cmd.visitDate() == null) {
            throw new ValidationException("Дата визита обязательна");
        }
        FieldValidation.requireValidSlot(cmd.timeFrom(), cmd.timeTo());
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
