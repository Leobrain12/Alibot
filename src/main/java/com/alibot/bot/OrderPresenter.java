package com.alibot.bot;

import com.alibot.bot.keyboard.Keyboards;
import com.alibot.domain.Order;
import com.alibot.domain.OrderStatus;
import com.alibot.service.AuthenticatedActor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

/**
 * ТЗ УИ-ТЗ п.4 — единый экран заявки: один и тот же рендер, состав действий меняется по
 * role+status. Используется и для первого показа (уведомление), и после каждого действия.
 */
@Component
public class OrderPresenter {

    public String renderCard(Order order) {
        return """
                <b>Заявка #%d</b>  [%s]
                %s · %s–%s
                %s%s
                Проблема: %s
                Клиент: %s, %s
                Адрес: %s
                Мастер: %s%s
                """.formatted(
                order.getNumber(), order.getStatus(),
                order.getVisitDate(), order.getTimeFrom(), order.getTimeTo(),
                order.getApplianceType(), order.getBrand() != null ? " · " + order.getBrand() : "",
                order.getSymptom(),
                order.getCustomerName(), order.getCustomerPhone(),
                order.getAddress(),
                order.getMaster() != null ? order.getMaster().getName() : "—",
                order.getFinalPrice() != null ? "\nИтого: " + order.getFinalPrice() + " ₽" : "");
    }

    public InlineKeyboardMarkup actionsFor(Order order, AuthenticatedActor actor) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        String id = order.getId().toString();

        if (actor.isMaster()) {
            OrderStatus s = order.getStatus();
            if (s == OrderStatus.ASSIGNED) {
                rows.add(row(bt("Принять", "ACC:" + id), bt("Отказаться", "DEC:" + id)));
            } else if (s == OrderStatus.ACCEPTED || s == OrderStatus.RESCHEDULED) {
                rows.add(row(bt("Выехал", "OTW:" + id)));
                rows.add(row(bt("Не дозвонился", "NC:" + id), bt("Перенести", "RESCH:" + id)));
            } else if (s == OrderStatus.ON_THE_WAY) {
                rows.add(row(bt("На месте", "ARR:" + id)));
            } else if (s == OrderStatus.ARRIVED) {
                rows.add(row(bt("Начать диагностику", "DIAG:" + id)));
            } else if (s == OrderStatus.DIAGNOSTICS) {
                rows.add(row(bt("Можно ремонтировать", "DGR:" + id + ":REPAIR")));
                rows.add(row(bt("Нужна деталь", "DGR:" + id + ":PART")));
                rows.add(row(bt("Ремонт нецелесообразен", "DGR:" + id + ":UNREP")));
                rows.add(row(bt("Клиент отказался", "DGR:" + id + ":CANCEL")));
            } else if (s == OrderStatus.PRICE_APPROVAL) {
                rows.add(row(bt("Клиент согласовал", "PRICE_OK:" + id)));
                rows.add(row(bt("Клиент отказался", "PRICE_NO:" + id)));
            } else if (s == OrderStatus.IN_PROGRESS) {
                rows.add(row(bt("Завершить ремонт", "REPORT:" + id)));
                rows.add(row(bt("Добавить медиа", "MED:" + id)));
                rows.add(row(bt("Нужна деталь", "DGR:" + id + ":PART")));
            } else if (s == OrderStatus.WAITING_PART) {
                rows.add(row(bt("Деталь пришла — выехал", "RPV:" + id)));
                rows.add(row(bt("Добавить фото/видео детали", "MED:" + id)));
            } else if (s == OrderStatus.NO_CONTACT) {
                rows.add(row(bt("Связались — продолжить", "NCOK:" + id)));
            }
        }

        if (actor.isAdmin() && !order.getStatus().isTerminal()) {
            rows.add(row(bt("Сменить мастера", "CHM:" + id), bt("Перенести", "RESCH:" + id)));
            rows.add(row(bt("Отменить", "CANCELORD:" + id)));
        }
        if (actor.isAdmin() && order.getStatus() == OrderStatus.NEW) {
            rows.add(row(bt("Назначить мастера", "CHM:" + id)));
        }
        if (actor.isAdmin() && order.getStatus() == OrderStatus.COMPLETED) {
            rows.add(row(bt("Оплачено полностью", "PAYFULL:" + id)));
        }
        if (actor.isAdmin() && (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.PAID)) {
            rows.add(row(bt("Гарантийное обращение", "WARR:" + id)));
        }

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private InlineKeyboardButton bt(String text, String data) {
        return Keyboards.button(text, data);
    }

    private InlineKeyboardRow row(InlineKeyboardButton... buttons) {
        return new InlineKeyboardRow(buttons);
    }
}
