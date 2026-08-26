package com.alibot.service;

import com.alibot.config.BotNotConfiguredCondition;
import com.alibot.domain.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Гарантирует, что бин NotificationGateway существует всегда, даже без Telegram-токена
 * (например при локальной проверке REST API/Mini App без бота) — иначе Spring-контекст не
 * поднимется, так как OrderService и другие сервисы жёстко зависят от NotificationGateway.
 * Активируется ровно тогда, когда TelegramNotificationGateway (bot-слой) не активируется —
 * условия взаимоисключающие, поэтому в контексте всегда ровно один бин NotificationGateway.
 */
@Component
@Conditional(BotNotConfiguredCondition.class)
@Slf4j
public class NoopNotificationGateway implements NotificationGateway {

    @Override
    public void masterAssigned(Order order) {
        log.debug("[noop-notify] masterAssigned order={}", order.getNumber());
    }

    @Override
    public void masterAcceptedNotifyAdmin(Order order) {
        log.debug("[noop-notify] masterAccepted order={}", order.getNumber());
    }

    @Override
    public void masterDeclinedNotifyAdmin(Order order, String reason) {
        log.debug("[noop-notify] masterDeclined order={} reason={}", order.getNumber(), reason);
    }

    @Override
    public void orderChangedNotifyMaster(Order order, String changeSummary) {
        log.debug("[noop-notify] orderChanged order={}", order.getNumber());
    }

    @Override
    public void masterTransferredNotifyOldMaster(Order order) {
        log.debug("[noop-notify] masterTransferred order={}", order.getNumber());
    }

    @Override
    public void partNeededNotifyAdmin(Order order, String partName, String comment) {
        log.debug("[noop-notify] partNeeded order={}", order.getNumber());
    }

    @Override
    public void orderCompletedNotifyAdmin(Order order) {
        log.debug("[noop-notify] orderCompleted order={}", order.getNumber());
    }

    @Override
    public void orderPaidNotifyAdmin(Order order) {
        log.debug("[noop-notify] orderPaid order={}", order.getNumber());
    }

    @Override
    public void warrantyCreatedNotifyAdmin(Order order) {
        log.debug("[noop-notify] warrantyCreated order={}", order.getNumber());
    }

    @Override
    public void masterNotAcceptedTimeout(Order order, int minutes) {
        log.debug("[noop-notify] notAcceptedTimeout order={}", order.getNumber());
    }

    @Override
    public void contactAttemptsExceededNotifyAdmin(Order order, int attempts) {
        log.debug("[noop-notify] contactAttemptsExceeded order={} attempts={}", order.getNumber(), attempts);
    }

    @Override
    public void leadCreatedNotifyAdmin(com.alibot.domain.Lead lead) {
        log.debug("[noop-notify] leadCreated id={}", lead.getId());
    }

    @Override
    public void reminder(Order order, int minutesBefore) {
        log.debug("[noop-notify] reminder order={}", order.getNumber());
    }

    @Override
    public void dailyDigest(StatsService.OverallStats stats) {
        log.debug("[noop-notify] dailyDigest ordersCreated={}", stats.ordersCreated());
    }
}
