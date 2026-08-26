package com.alibot.service;

import com.alibot.config.AppProperties;
import com.alibot.domain.ContactAttempt;
import com.alibot.domain.ContactResult;
import com.alibot.domain.Order;
import com.alibot.repository.ContactAttemptRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ТЗ п.36-38 — фиксация недозвонов; после N попыток админ может перевести заказ в NO_CONTACT. */
@Service
@RequiredArgsConstructor
@Transactional
public class ContactAttemptService {

    private final ContactAttemptRepository contactAttemptRepository;
    private final AccessControlService accessControl;
    private final AppProperties appProperties;
    private final OrderService orderService;
    private final NotificationGateway notifications;

    public ContactAttempt recordAttempt(UUID orderId, ContactResult result, String comment, AuthenticatedActor actor) {
        Order order = orderService.findOrThrow(orderId);
        accessControl.assertIsAssignedMaster(actor, order);

        ContactAttempt attempt = ContactAttempt.builder()
                .orderId(orderId)
                .userId(actor.userId())
                .result(result)
                .comment(comment)
                .build();
        attempt = contactAttemptRepository.save(attempt);

        // ТЗ п.38 — рекомендация, не автоматическое действие: решение переводить заказ в
        // NO_CONTACT остаётся за админом (markNoContact). Уведомляем один раз, ровно в момент
        // пересечения порога, а не при каждой следующей попытке — иначе на 5-м/6-м недозвоне
        // админ получил бы то же сообщение снова и снова.
        long attemptsCount = contactAttemptRepository.countByOrderId(orderId);
        if (attemptsCount == appProperties.getOrder().getMaxContactAttempts()) {
            notifications.contactAttemptsExceededNotifyAdmin(order, (int) attemptsCount);
        }
        return attempt;
    }

    @Transactional(readOnly = true)
    public List<ContactAttempt> history(UUID orderId, AuthenticatedActor actor) {
        Order order = orderService.getById(orderId, actor);
        return contactAttemptRepository.findByOrderIdOrderByAttemptedAtDesc(order.getId());
    }
}
