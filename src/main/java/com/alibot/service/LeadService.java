package com.alibot.service;

import com.alibot.domain.Lead;
import com.alibot.domain.LeadStatus;
import com.alibot.domain.Order;
import com.alibot.repository.LeadRepository;
import com.alibot.service.dto.CreateOrderCommand;
import com.alibot.service.dto.SubmitLeadCommand;
import com.alibot.service.exception.NotFoundException;
import com.alibot.service.exception.ValidationException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ТЗ п.10-11 — Lead (маркетинговая заявка) и её превращение в Order (уже квалифицированную задачу
 * на ремонт). Единственное место, принимающее решения о лиде — тот же принцип, что и OrderService
 * для заказа: ни бот, ни REST-контроллер не содержат собственных правил.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class LeadService {

    private final LeadRepository leadRepository;
    private final AccessControlService accessControl;
    private final OrderService orderService;
    private final NotificationGateway notifications;
    private final AuditLogService auditLog;

    /** ТЗ п.17.2 — внешние системы (сайт/CRM) присылают лид через Internal API; тот же путь
     *  доступен и ADMIN/SUPERADMIN вручную (например, лид с телефонного звонка). */
    public Lead submit(SubmitLeadCommand cmd, AuthenticatedActor actor) {
        accessControl.assertIsAdmin(actor);
        FieldValidation.requireNonBlank(cmd.customerName(), "Имя клиента обязательно");
        FieldValidation.requireNonBlank(cmd.customerPhone(), "Телефон клиента обязателен");

        Lead lead = Lead.builder()
                .customerName(cmd.customerName())
                .customerPhone(cmd.customerPhone())
                .applianceType(cmd.applianceType())
                .comment(cmd.comment())
                .source(cmd.source())
                .externalId(cmd.externalId())
                .status(LeadStatus.NEW)
                .build();
        lead = leadRepository.save(lead);
        auditLog.record("LEAD_CREATED", "LEAD", lead.getId(), actor.userId(), null,
                lead.getSource() == null ? "" : lead.getSource());
        notifications.leadCreatedNotifyAdmin(lead);
        return lead;
    }

    @Transactional(readOnly = true)
    public List<Lead> listPending(AuthenticatedActor actor) {
        accessControl.assertIsAdmin(actor);
        return leadRepository.findByStatusOrderByCreatedAtAsc(LeadStatus.NEW);
    }

    @Transactional(readOnly = true)
    public List<Lead> listAll(AuthenticatedActor actor) {
        accessControl.assertIsAdmin(actor);
        return leadRepository.findAllByOrderByCreatedAtDesc();
    }

    /** Квалификация лида: админ дозаполняет то, чего в лиде ещё не было (адрес, дату визита,
     *  мастера и т.п.) — тот же CreateOrderCommand, что и при обычном создании заказа, только
     *  order.leadId проставляется автоматически, а не полагается на то, что вызывающий его не
     *  забудет передать. */
    public Order convertToOrder(UUID leadId, CreateOrderCommand orderFields, AuthenticatedActor actor) {
        accessControl.assertIsAdmin(actor);
        Lead lead = findOrThrow(leadId);
        if (lead.getStatus() != LeadStatus.NEW) {
            throw new ValidationException("Лид уже обработан (%s)".formatted(lead.getStatus()));
        }

        CreateOrderCommand withLeadLink = new CreateOrderCommand(
                orderFields.customerName(), orderFields.customerPhone(), orderFields.applianceType(),
                orderFields.brand(), orderFields.model(), orderFields.symptom(), orderFields.description(),
                orderFields.address(), orderFields.visitDate(), orderFields.timeFrom(), orderFields.timeTo(),
                orderFields.masterId(), orderFields.adminComment(),
                lead.getId().toString(), lead.getExternalId(),
                orderFields.source() != null ? orderFields.source() : lead.getSource());
        Order order = orderService.create(withLeadLink, actor);

        lead.setStatus(LeadStatus.CONVERTED);
        lead.setConvertedOrderId(order.getId());
        lead.setProcessedAt(Instant.now());
        leadRepository.save(lead);
        auditLog.record("LEAD_CONVERTED", "LEAD", lead.getId(), actor.userId(), "NEW",
                "ORDER #" + order.getNumber());
        return order;
    }

    public Lead reject(UUID leadId, String reason, AuthenticatedActor actor) {
        accessControl.assertIsAdmin(actor);
        FieldValidation.requireNonBlank(reason, "Причина отклонения обязательна");
        Lead lead = findOrThrow(leadId);
        if (lead.getStatus() != LeadStatus.NEW) {
            throw new ValidationException("Лид уже обработан (%s)".formatted(lead.getStatus()));
        }
        lead.setStatus(LeadStatus.REJECTED);
        lead.setRejectReason(reason);
        lead.setProcessedAt(Instant.now());
        lead = leadRepository.save(lead);
        auditLog.record("LEAD_REJECTED", "LEAD", lead.getId(), actor.userId(), "NEW", reason);
        return lead;
    }

    private Lead findOrThrow(UUID id) {
        return leadRepository.findById(id).orElseThrow(() -> new NotFoundException("Лид не найден"));
    }
}
