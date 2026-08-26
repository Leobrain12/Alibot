package com.alibot.service;

import com.alibot.config.AppProperties;
import com.alibot.domain.Order;
import com.alibot.domain.OrderStatus;
import com.alibot.domain.OrderStatusHistory;
import com.alibot.repository.OrderRepository;
import com.alibot.repository.OrderStatusHistoryRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Плановые проверки, которые ТЗ описывает как "фоновые" уведомления, а не реакцию на действие
 * пользователя: напоминание мастеру перед визитом (п.83), эскалация неподтверждённой заявки
 * (п.84) и ежедневная сводка администратору (п.79). Живёт в service/, а не в bot/ — это
 * бизнес-правило "когда нужно напомнить", транспорт уведомления (Telegram) — уже в
 * NotificationGateway, который сюда только вызывается.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderTimersScheduler {

    private static final List<OrderStatus> REMINDABLE_STATUSES =
            List.of(OrderStatus.ACCEPTED, OrderStatus.ON_THE_WAY, OrderStatus.RESCHEDULED);

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final StatsService statsService;
    private final AppProperties appProperties;
    private final NotificationGateway notifications;

    @Scheduled(cron = "${app.schedule.timers-cron}")
    @Transactional
    public void checkAcceptTimeouts() {
        int timeoutMinutes = appProperties.getOrder().getAcceptTimeoutMinutes();
        List<Order> pending = orderRepository.findByStatusAndAcceptTimeoutNotifiedAtIsNull(OrderStatus.ASSIGNED);
        for (Order order : pending) {
            OrderStatusHistory lastChange = historyRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId())
                    .orElse(null);
            if (lastChange == null) {
                continue;
            }
            long minutesWaiting = ChronoUnit.MINUTES.between(lastChange.getCreatedAt(), Instant.now());
            if (minutesWaiting >= timeoutMinutes) {
                notifications.masterNotAcceptedTimeout(order, timeoutMinutes);
                order.setAcceptTimeoutNotifiedAt(Instant.now());
                orderRepository.save(order);
            }
        }
    }

    @Scheduled(cron = "${app.schedule.timers-cron}")
    @Transactional
    public void checkReminders() {
        int reminderMinutes = appProperties.getOrder().getReminderMinutes();
        List<Order> upcoming = orderRepository.findByStatusInAndReminderSentAtIsNullAndMasterIsNotNull(REMINDABLE_STATUSES);
        LocalDateTime now = LocalDateTime.now();
        for (Order order : upcoming) {
            LocalDateTime visitAt = LocalDateTime.of(order.getVisitDate(), order.getTimeFrom());
            long minutesUntilVisit = ChronoUnit.MINUTES.between(now, visitAt);
            if (minutesUntilVisit >= 0 && minutesUntilVisit <= reminderMinutes) {
                notifications.reminder(order, (int) minutesUntilVisit);
                order.setReminderSentAt(Instant.now());
                orderRepository.save(order);
            }
        }
    }

    @Scheduled(cron = "${app.schedule.daily-report-cron}")
    public void sendDailyDigest() {
        Instant from = LocalDateTime.now().toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant to = Instant.now();
        StatsService.OverallStats stats = statsService.overallStats(from, to, AuthenticatedActor.system(null));
        notifications.dailyDigest(stats);
        log.info("Отправлена ежедневная сводка: {} новых заказов, {} выполнено", stats.ordersCreated(), stats.completedOrders());
    }
}
