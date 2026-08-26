package com.alibot.service;

import com.alibot.config.AppProperties;
import com.alibot.domain.CrmSyncQueueItem;
import com.alibot.domain.CrmSyncStatus;
import com.alibot.domain.Order;
import com.alibot.repository.CrmSyncQueueRepository;
import com.alibot.service.exception.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ТЗ п.85-87 — CRMAdapter: постановка событий заказа в очередь синхронизации и их доставка
 * с ретраями. Единственное место, которое знает про очередь; CrmSyncGateway — только транспорт.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CrmSyncService {

    /** Экспоненциальная задержка между попытками: 2, 10, 30, 120 минут, дальше — FAILED. */
    private static final int[] BACKOFF_MINUTES = {2, 10, 30, 120};

    private final CrmSyncQueueRepository queueRepository;
    private final CrmSyncGateway gateway;
    private final AppProperties appProperties;
    private final AccessControlService accessControl;
    private final ObjectMapper objectMapper;

    /** Вызывается из OrderService/PaymentService на те же события, что и AuditLog (создание,
     *  смена статуса, оплата) — не пишет в очередь вовсе, если CRM не настроена. */
    public void enqueue(Order order, String eventType) {
        if (!gateway.isEnabled()) {
            return;
        }
        CrmSyncQueueItem item = CrmSyncQueueItem.builder()
                .orderId(order.getId())
                .eventType(eventType)
                .payload(toPayload(order, eventType))
                .status(CrmSyncStatus.PENDING)
                .nextAttemptAt(Instant.now())
                .build();
        queueRepository.save(item);
    }

    private String toPayload(Order order, String eventType) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("eventType", eventType);
        data.put("orderId", order.getId());
        data.put("orderNumber", order.getNumber());
        data.put("status", order.getStatus().name());
        data.put("leadId", order.getLeadId());
        data.put("crmId", order.getCrmId());
        data.put("source", order.getSource());
        data.put("customerName", order.getCustomerName());
        data.put("customerPhone", order.getCustomerPhone());
        data.put("applianceType", order.getApplianceType());
        data.put("address", order.getAddress());
        data.put("visitDate", order.getVisitDate());
        data.put("masterName", order.getMaster() != null ? order.getMaster().getName() : null);
        data.put("finalPrice", order.getFinalPrice());
        data.put("timestamp", Instant.now());
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать событие для CRM", e);
        }
    }

    /** Вызывается планировщиком (CrmSyncScheduler). Отдельная транзакция на каждую попытку —
     *  сбой одной записи не должен откатывать успешно обработанные соседние. */
    @Transactional
    public void attemptDelivery(UUID itemId) {
        CrmSyncQueueItem item = queueRepository.findById(itemId).orElse(null);
        if (item == null || item.getStatus() != CrmSyncStatus.PENDING) {
            return;
        }
        try {
            gateway.send(item.getEventType(), item.getPayload());
            item.setStatus(CrmSyncStatus.SENT);
            item.setLastError(null);
            queueRepository.save(item);
        } catch (Exception e) {
            int attempts = item.getAttempts() + 1;
            item.setAttempts(attempts);
            item.setLastError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            if (attempts > BACKOFF_MINUTES.length || attempts >= appProperties.getCrm().getMaxAttempts()) {
                item.setStatus(CrmSyncStatus.FAILED);
                log.warn("CRM sync: событие {} заказа {} помечено FAILED после {} попыток",
                        item.getEventType(), item.getOrderId(), attempts);
            } else {
                int delayMinutes = BACKOFF_MINUTES[attempts - 1];
                item.setNextAttemptAt(Instant.now().plus(delayMinutes, ChronoUnit.MINUTES));
            }
            queueRepository.save(item);
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> pendingIds(Instant now) {
        return queueRepository.findByStatusAndNextAttemptAtBefore(CrmSyncStatus.PENDING, now)
                .stream().map(CrmSyncQueueItem::getId).toList();
    }

    @Transactional(readOnly = true)
    public List<CrmSyncQueueItem> listFailed(AuthenticatedActor actor) {
        accessControl.assertIsAdmin(actor);
        return queueRepository.findByStatusOrderByUpdatedAtDesc(CrmSyncStatus.FAILED);
    }

    /** Ручной повторный запуск (ТЗ п.87) — переводит запись обратно в PENDING немедленно. */
    public void retry(UUID id, AuthenticatedActor actor) {
        accessControl.assertIsAdmin(actor);
        CrmSyncQueueItem item = queueRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Запись очереди CRM-синхронизации не найдена"));
        item.setStatus(CrmSyncStatus.PENDING);
        item.setNextAttemptAt(Instant.now());
        queueRepository.save(item);
    }
}
