package com.alibot.bot;

import com.alibot.config.BotConfiguredCondition;
import com.alibot.domain.Order;
import com.alibot.domain.Role;
import com.alibot.domain.User;
import com.alibot.repository.UserRepository;
import com.alibot.service.NotificationGateway;
import com.alibot.service.StatsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Единственная реализация service.NotificationGateway — вся Telegram-специфика (форматирование
 * сообщений, резолвинг chat_id) находится здесь, а не в OrderService/WorkReportService и т.п.
 * ТЗ п.81/82 — какие уведомления получает мастер и какие администратор.
 */
@Component
@Conditional(BotConfiguredCondition.class)
@RequiredArgsConstructor
public class TelegramNotificationGateway implements NotificationGateway {

    private final BotSender sender;
    private final UserRepository userRepository;

    @Override
    public void masterAssigned(Order order) {
        if (order.getMaster() == null) {
            return;
        }
        long chatId = order.getMaster().getUser().getTelegramUserId();
        String text = """
                <b>Новая заявка #%d</b>
                Дата: %s
                Время: %s–%s
                Техника: %s%s
                Проблема: %s
                Адрес: %s
                Клиент: %s
                Телефон: %s%s
                """.formatted(order.getNumber(), order.getVisitDate(), order.getTimeFrom(), order.getTimeTo(),
                order.getApplianceType(), order.getBrand() != null ? " · " + order.getBrand() : "",
                order.getSymptom(), order.getAddress(), order.getCustomerName(), order.getCustomerPhone(),
                order.getAdminComment() != null ? "\nКомментарий администратора: " + order.getAdminComment() : "");
        sender.send(chatId, text, com.alibot.bot.keyboard.Keyboards.of(
                "Принять", "ACC:" + order.getId(),
                "Отказаться", "DEC:" + order.getId()));
    }

    @Override
    public void masterAcceptedNotifyAdmin(Order order) {
        notifyAdmins("Мастер %s принял заявку #%d".formatted(order.getMaster().getName(), order.getNumber()));
    }

    @Override
    public void masterDeclinedNotifyAdmin(Order order, String reason) {
        notifyAdmins("Мастер отказался от заявки #%d.\nПричина: %s".formatted(order.getNumber(), reason));
    }

    @Override
    public void orderChangedNotifyMaster(Order order, String changeSummary) {
        if (order.getMaster() == null) {
            return;
        }
        sender.send(order.getMaster().getUser().getTelegramUserId(),
                "Изменение по заявке #%d: %s".formatted(order.getNumber(), changeSummary));
    }

    @Override
    public void masterTransferredNotifyOldMaster(Order order) {
        notifyAdmins("Заявка #%d передана другому мастеру".formatted(order.getNumber()));
    }

    @Override
    public void partNeededNotifyAdmin(Order order, String partName, String comment) {
        notifyAdmins("""
                По заявке #%d требуется запчасть.
                Мастер: %s
                Деталь: %s
                Комментарий: %s
                """.formatted(order.getNumber(), order.getMaster() != null ? order.getMaster().getName() : "-",
                partName, comment == null ? "-" : comment));
    }

    @Override
    public void orderCompletedNotifyAdmin(Order order) {
        notifyAdmins("""
                Заявка #%d выполнена.
                Мастер: %s
                Итого клиенту: %s ₽
                """.formatted(order.getNumber(), order.getMaster() != null ? order.getMaster().getName() : "-",
                order.getFinalPrice()));
    }

    @Override
    public void orderPaidNotifyAdmin(Order order) {
        notifyAdmins("Заявка #%d полностью оплачена (%s ₽)".formatted(order.getNumber(), order.getFinalPrice()));
    }

    @Override
    public void warrantyCreatedNotifyAdmin(Order order) {
        notifyAdmins("По заявке #%d открыто гарантийное обращение".formatted(order.getNumber()));
    }

    @Override
    public void masterNotAcceptedTimeout(Order order, int minutes) {
        notifyAdmins("Мастер не подтвердил заявку #%d за %d минут.".formatted(order.getNumber(), minutes));
    }

    @Override
    public void contactAttemptsExceededNotifyAdmin(Order order, int attempts) {
        notifyAdmins("Заявка #%d: %d недозвонов подряд. Рекомендуем закрыть заказ (перевести в «Недозвон»)."
                .formatted(order.getNumber(), attempts));
    }

    @Override
    public void leadCreatedNotifyAdmin(com.alibot.domain.Lead lead) {
        notifyAdmins("Новый лид: %s, %s%s\nОбработать — в Mini App, раздел «Лиды»."
                .formatted(lead.getCustomerName(), lead.getCustomerPhone(),
                        lead.getSource() != null ? " (источник: " + lead.getSource() + ")" : ""));
    }

    @Override
    public void reminder(Order order, int minutesBefore) {
        if (order.getMaster() == null) {
            return;
        }
        sender.send(order.getMaster().getUser().getTelegramUserId(),
                "Через %d минут заявка #%d.\n%s–%s\n%s"
                        .formatted(minutesBefore, order.getNumber(), order.getTimeFrom(), order.getTimeTo(),
                                order.getAddress()));
    }

    @Override
    public void dailyDigest(StatsService.OverallStats stats) {
        notifyAdmins("""
                <b>Итоги дня</b>
                Новых заказов: %d
                Выполнено: %d
                Выручка: %s ₽
                Ожидают деталь: %d
                Отменено клиентом: %d
                Гарантийных: %d
                """.formatted(stats.ordersCreated(), stats.completedOrders(), stats.revenue(),
                stats.waitingPart(), stats.customerCancellations(), stats.warrantyOrders()));
    }

    private void notifyAdmins(String text) {
        List<User> admins = userRepository.findByRoleInAndActiveTrue(List.of(Role.ADMIN, Role.SUPERADMIN));
        for (User admin : admins) {
            sender.send(admin.getTelegramUserId(), text);
        }
    }
}
