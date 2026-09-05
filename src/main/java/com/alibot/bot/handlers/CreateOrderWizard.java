package com.alibot.bot.handlers;

import com.alibot.bot.BotSender;
import com.alibot.bot.keyboard.Keyboards;
import com.alibot.config.BotConfiguredCondition;
import com.alibot.service.ReferenceDataService;
import com.alibot.domain.ConversationState;
import com.alibot.domain.Master;
import com.alibot.domain.Order;
import com.alibot.service.AuthenticatedActor;
import com.alibot.service.ConversationStateService;
import com.alibot.service.MasterService;
import com.alibot.service.OrderService;
import com.alibot.service.dto.CreateOrderCommand;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * ТЗ п.18 — создание заявки администратором, 12 шагов. Прогресс живёт в ConversationState
 * (server-side FSM), а сама заявка в итоге создаётся через OrderService.create() — тот же метод,
 * которым пользуется REST API (сайт/CRM) и Mini App.
 */
@Component
@Conditional(BotConfiguredCondition.class)
@RequiredArgsConstructor
public class CreateOrderWizard {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    public static final String SCENARIO = "CREATE_ORDER";

    private final ConversationStateService conversations;
    private final ReferenceDataService catalog;
    private final MasterService masterService;
    private final OrderService orderService;
    private final BotSender sender;

    /** editMessageId — сообщение с нажатой кнопкой (например MENU_NEW_ORDER), которое правим на
     *  месте первым шагом визарда вместо отправки нового; null, если визард запущен не по кнопке
     *  (команда /new_order — тогда просто шлём новое сообщение). */
    public void start(long chatId, long telegramUserId, Integer editMessageId) {
        ConversationState state = conversations.start(chatId, telegramUserId, SCENARIO, "APPLIANCE_TYPE", null);
        sender.editOrSend(chatId, editMessageId, "Шаг 1 из 12. Тип техники:", applianceKeyboard());
    }

    public void handleCallback(ConversationState state, String data, AuthenticatedActor actor) {
        Map<String, String> draft = conversations.readDraft(state);
        long chatId = state.getChatId();

        switch (state.getStep()) {
            case "APPLIANCE_TYPE" -> {
                draft.put("applianceType", data);
                conversations.update(state, "BRAND", draft);
                sender.send(chatId, "Шаг 2 из 12. Бренд:", brandKeyboard());
            }
            case "BRAND" -> {
                draft.put("brand", "Другое".equals(data) || "Неизвестно".equals(data) ? null : data);
                conversations.update(state, "MODEL", draft);
                sender.send(chatId, "Шаг 3 из 12. Модель (текстом) или пропустите:",
                        Keyboards.of("Пропустить", "CO_SKIP"));
            }
            case "MODEL" -> advanceToProblem(state, draft, chatId);
            case "DESCRIPTION" -> advanceToClient(state, draft, chatId);
            case "DATE" -> {
                LocalDate date = switch (data) {
                    case "Сегодня" -> LocalDate.now();
                    case "Завтра" -> LocalDate.now().plusDays(1);
                    case "Послезавтра" -> LocalDate.now().plusDays(2);
                    default -> null;
                };
                if (date == null) {
                    sender.send(chatId, "Введите дату в формате ДД.ММ.ГГГГ:");
                } else {
                    draft.put("visitDate", date.format(DATE_FMT));
                    conversations.update(state, "SLOT", draft);
                    sender.send(chatId, "Шаг 10 из 12. Временной слот:", slotKeyboard());
                }
            }
            case "SLOT" -> {
                String[] fromTo = data.split("-");
                draft.put("timeFrom", fromTo[0]);
                draft.put("timeTo", fromTo[1]);
                conversations.update(state, "MASTER", draft);
                showMasters(draft, chatId, actor);
            }
            case "MASTER" -> {
                draft.put("masterId", "NONE".equals(data) ? null : data);
                conversations.update(state, "CONFIRM", draft);
                sender.send(chatId, confirmationText(draft), Keyboards.of(
                        "Создать", "CO_CREATE",
                        "Отмена", "CO_CANCEL"));
            }
            case "CONFIRM" -> {
                if ("CO_CREATE".equals(data)) {
                    Order order = create(draft, actor);
                    conversations.complete(state);
                    sender.send(chatId, "Заявка #%d создана%s".formatted(order.getNumber(),
                            order.getMaster() != null ? " и назначена мастеру " + order.getMaster().getName() : ""));
                } else {
                    conversations.complete(state);
                    sender.send(chatId, "Создание заявки отменено.");
                }
            }
            default -> sender.send(chatId, "Неожиданный шаг сценария, начните заново: /new_order");
        }
    }

    public void handleText(ConversationState state, String text, AuthenticatedActor actor) {
        Map<String, String> draft = conversations.readDraft(state);
        long chatId = state.getChatId();

        switch (state.getStep()) {
            case "MODEL" -> {
                draft.put("model", text);
                advanceToProblem(state, draft, chatId);
            }
            case "PROBLEM" -> {
                draft.put("symptom", text);
                conversations.update(state, "DESCRIPTION", draft);
                sender.send(chatId, "Шаг 5 из 12. Комментарий администратора (или '-' чтобы пропустить):");
            }
            case "DESCRIPTION" -> {
                draft.put("description", "-".equals(text.trim()) ? null : text);
                advanceToClient(state, draft, chatId);
            }
            case "CLIENT_NAME" -> {
                draft.put("customerName", text);
                conversations.update(state, "PHONE", draft);
                sender.send(chatId, "Шаг 7 из 12. Телефон клиента:");
            }
            case "PHONE" -> {
                if (text.replaceAll("\\D", "").length() < 10) {
                    sender.send(chatId, "Похоже на некорректный номер, попробуйте ещё раз:");
                    return;
                }
                draft.put("customerPhone", text);
                conversations.update(state, "ADDRESS", draft);
                sender.send(chatId, "Шаг 8 из 12. Адрес:");
            }
            case "ADDRESS" -> {
                draft.put("address", text);
                conversations.update(state, "DATE", draft);
                sender.send(chatId, "Шаг 9 из 12. Дата визита:", Keyboards.of(
                        "Сегодня", "Сегодня", "Завтра", "Завтра", "Послезавтра", "Послезавтра"));
            }
            case "DATE" -> {
                try {
                    LocalDate date = LocalDate.parse(text.trim(), DATE_FMT);
                    draft.put("visitDate", date.format(DATE_FMT));
                    conversations.update(state, "SLOT", draft);
                    sender.send(chatId, "Шаг 10 из 12. Временной слот:", slotKeyboard());
                } catch (Exception e) {
                    sender.send(chatId, "Не понял дату, формат ДД.ММ.ГГГГ:");
                }
            }
            default -> sender.send(chatId, "На этом шаге ожидается выбор кнопкой, а не текст.");
        }
    }

    private void advanceToProblem(ConversationState state, Map<String, String> draft, long chatId) {
        conversations.update(state, "PROBLEM", draft);
        sender.send(chatId, "Шаг 4 из 12. Опишите проблему:");
    }

    private void advanceToClient(ConversationState state, Map<String, String> draft, long chatId) {
        conversations.update(state, "CLIENT_NAME", draft);
        sender.send(chatId, "Шаг 6 из 12. Имя клиента:");
    }

    private void showMasters(Map<String, String> draft, long chatId, AuthenticatedActor actor) {
        List<Master> suitable = masterService.findSuitable(draft.get("applianceType"), draft.get("brand"), null, actor);
        List<String[]> options = new ArrayList<>();
        for (Master m : suitable) {
            options.add(new String[]{m.getName() + " (" + String.join(",", m.getApplianceTypes()) + ")", m.getId().toString()});
        }
        options.add(new String[]{"Без мастера (оставить нераспределённой)", "NONE"});
        sender.send(chatId, "Шаг 11 из 12. Мастер:", Keyboards.singleColumn(options));
    }

    private String confirmationText(Map<String, String> draft) {
        return """
                Шаг 12 из 12. Подтверждение
                Техника: %s%s
                Модель: %s
                Проблема: %s
                Клиент: %s, %s
                Адрес: %s
                Дата: %s, %s–%s
                """.formatted(draft.get("applianceType"),
                draft.get("brand") != null ? " · " + draft.get("brand") : "",
                draft.getOrDefault("model", "-"),
                draft.get("symptom"),
                draft.get("customerName"), draft.get("customerPhone"),
                draft.get("address"),
                draft.get("visitDate"), draft.get("timeFrom"), draft.get("timeTo"));
    }

    private Order create(Map<String, String> draft, AuthenticatedActor actor) {
        CreateOrderCommand cmd = new CreateOrderCommand(
                draft.get("customerName"),
                draft.get("customerPhone"),
                draft.get("applianceType"),
                draft.get("brand"),
                draft.get("model"),
                draft.get("symptom"),
                draft.get("description"),
                draft.get("address"),
                LocalDate.parse(draft.get("visitDate"), DATE_FMT),
                LocalTime.parse(draft.get("timeFrom")),
                LocalTime.parse(draft.get("timeTo")),
                draft.get("masterId") != null ? UUID.fromString(draft.get("masterId")) : null,
                null, null, null, "telegram-admin"
        );
        return orderService.create(cmd, actor);
    }

    private org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup applianceKeyboard() {
        List<String[]> options = catalog.getApplianceTypes().stream().map(t -> new String[]{t, t}).toList();
        return Keyboards.grid(options, 2);
    }

    private org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup brandKeyboard() {
        List<String[]> options = catalog.getPopularBrands().stream().map(b -> new String[]{b, b}).toList();
        return Keyboards.grid(options, 3);
    }

    private org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup slotKeyboard() {
        List<String[]> options = catalog.getTimeSlots().stream().map(s -> new String[]{s, s}).toList();
        return Keyboards.grid(options, 2);
    }
}
