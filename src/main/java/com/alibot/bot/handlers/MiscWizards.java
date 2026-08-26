package com.alibot.bot.handlers;

import com.alibot.bot.BotSender;
import com.alibot.bot.OrderPresenter;
import com.alibot.bot.keyboard.Keyboards;
import com.alibot.config.BotConfiguredCondition;
import com.alibot.service.ReferenceDataService;
import com.alibot.domain.ConversationState;
import com.alibot.domain.MediaStage;
import com.alibot.domain.Master;
import com.alibot.domain.Order;
import com.alibot.service.AuthenticatedActor;
import com.alibot.service.ConversationStateService;
import com.alibot.service.MasterService;
import com.alibot.service.OrderService;
import com.alibot.service.dto.PriceApprovalCommand;
import com.alibot.service.dto.RescheduleCommand;
import com.alibot.service.dto.WaitingPartCommand;
import com.alibot.service.dto.WarrantyCommand;
import java.math.BigDecimal;
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
 * Компактные сценарии на 1-5 полей: отказ мастера (причина), отказ клиента (причина), нужна
 * деталь, перенос, отмена админом, гарантийное обращение. Каждый — такой же server-side FSM
 * через ConversationState, что и CreateOrderWizard/WorkReportWizard, и в конце вызывает тот же
 * OrderService, которым пользуется REST API.
 */
@Component
@Conditional(BotConfiguredCondition.class)
@RequiredArgsConstructor
public class MiscWizards {

    public static final String DECLINE_REASON = "DECLINE_REASON";
    public static final String PRICE_DECLINE_REASON = "PRICE_DECLINE_REASON";
    public static final String WAITING_PART = "WAITING_PART";
    public static final String RESCHEDULE = "RESCHEDULE";
    public static final String CANCEL_REASON = "CANCEL_REASON";
    public static final String UNREPAIRABLE_REASON = "UNREPAIRABLE_REASON";
    public static final String WARRANTY = "WARRANTY";
    public static final String MEDIA = "MEDIA";
    public static final String PRICE_APPROVAL_INPUT = "PRICE_APPROVAL_INPUT";
    public static final String SEARCH = "SEARCH";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final ConversationStateService conversations;
    private final ReferenceDataService catalog;
    private final OrderService orderService;
    private final MasterService masterService;
    private final OrderPresenter presenter;
    private final BotSender sender;

    // --- Запуск сценариев (из CallbackRouter) ---

    public void startDeclineReason(UUID orderId, long chatId, long telegramUserId) {
        conversations.start(chatId, telegramUserId, DECLINE_REASON, "REASON", orderId);
        sender.send(chatId, "Причина отказа:", reasonKeyboard(catalog.getMasterDeclineReasons(), "DR"));
    }

    public void startPriceDeclineReason(UUID orderId, long chatId, long telegramUserId) {
        conversations.start(chatId, telegramUserId, PRICE_DECLINE_REASON, "REASON", orderId);
        sender.send(chatId, "Причина отказа клиента:", reasonKeyboard(catalog.getCustomerCancelReasons(), "PR"));
    }

    public void startWaitingPart(UUID orderId, long chatId, long telegramUserId) {
        conversations.start(chatId, telegramUserId, WAITING_PART, "PART_NAME", orderId);
        sender.send(chatId, "Название детали:");
    }

    public void startReschedule(UUID orderId, long chatId, long telegramUserId) {
        conversations.start(chatId, telegramUserId, RESCHEDULE, "DATE", orderId);
        sender.send(chatId, "Новая дата (ДД.ММ.ГГГГ):");
    }

    public void startCancel(UUID orderId, long chatId, long telegramUserId) {
        conversations.start(chatId, telegramUserId, CANCEL_REASON, "REASON", orderId);
        sender.send(chatId, "Причина отмены заказа:");
    }

    public void startUnrepairable(UUID orderId, long chatId, long telegramUserId) {
        conversations.start(chatId, telegramUserId, UNREPAIRABLE_REASON, "REASON", orderId);
        sender.send(chatId, "Почему ремонт нецелесообразен:");
    }

    public void startWarranty(UUID orderId, long chatId, long telegramUserId) {
        conversations.start(chatId, telegramUserId, WARRANTY, "PROBLEM", orderId);
        sender.send(chatId, "Опишите проблему по гарантии:");
    }

    public void startMedia(UUID orderId, long chatId, long telegramUserId) {
        conversations.start(chatId, telegramUserId, MEDIA, "STAGE", orderId);
        sender.send(chatId, "Этап съёмки:", stageKeyboard());
    }

    public void startPriceApprovalInput(UUID orderId, long chatId, long telegramUserId) {
        conversations.start(chatId, telegramUserId, PRICE_APPROVAL_INPUT, "FAILURE_REASON", orderId);
        sender.send(chatId, "Причина неисправности:");
    }

    public void startSearch(long chatId, long telegramUserId) {
        conversations.start(chatId, telegramUserId, SEARCH, "QUERY", null);
        sender.send(chatId, "Введите номер заказа / телефон / имя / адрес:");
    }

    // --- Обработка ---

    public void handleCallback(ConversationState state, String data, AuthenticatedActor actor) {
        Map<String, String> draft = conversations.readDraft(state);
        long chatId = state.getChatId();
        UUID orderId = state.getRelatedOrderId();

        switch (state.getScenario()) {
            case DECLINE_REASON -> {
                if (data.equals("DR:OTHER")) {
                    conversations.update(state, "REASON_TEXT", draft);
                    sender.send(chatId, "Опишите причину:");
                } else {
                    String reason = catalog.getMasterDeclineReasons().get(Integer.parseInt(data.split(":")[1]));
                    orderService.declineByMaster(orderId, reason, actor);
                    conversations.complete(state);
                    sender.send(chatId, "Отказ зафиксирован.");
                }
            }
            case PRICE_DECLINE_REASON -> {
                if (data.equals("PR:OTHER")) {
                    conversations.update(state, "REASON_TEXT", draft);
                    sender.send(chatId, "Опишите причину:");
                } else {
                    String reason = catalog.getCustomerCancelReasons().get(Integer.parseInt(data.split(":")[1]));
                    orderService.declineByCustomer(orderId, reason, actor);
                    conversations.complete(state);
                    sender.send(chatId, "Отказ клиента зафиксирован.");
                }
            }
            case RESCHEDULE -> {
                if ("SLOT".equals(state.getStep())) {
                    String[] fromTo = data.split("-");
                    draft.put("timeFrom", fromTo[0]);
                    draft.put("timeTo", fromTo[1]);
                    conversations.update(state, "REASON", draft);
                    sender.send(chatId, "Причина переноса:", reasonKeyboard(catalog.getRescheduleReasons(), "RS"));
                } else if ("REASON".equals(state.getStep())) {
                    if (data.equals("RS:OTHER")) {
                        conversations.update(state, "REASON_TEXT", draft);
                        sender.send(chatId, "Опишите причину переноса:");
                    } else {
                        String reason = catalog.getRescheduleReasons().get(Integer.parseInt(data.split(":")[1]));
                        finishReschedule(state, draft, reason, actor);
                    }
                }
            }
            case WARRANTY -> {
                if ("SLOT".equals(state.getStep())) {
                    String[] fromTo = data.split("-");
                    draft.put("timeFrom", fromTo[0]);
                    draft.put("timeTo", fromTo[1]);
                    conversations.update(state, "MASTER", draft);
                    showMasters(draft, chatId, actor);
                } else if ("MASTER".equals(state.getStep())) {
                    draft.put("masterId", "NONE".equals(data) ? null : data);
                    conversations.update(state, "COMMENT", draft);
                    sender.send(chatId, "Комментарий (или '-'):");
                }
            }
            case MEDIA -> {
                if ("STAGE".equals(state.getStep())) {
                    draft.put("stage", data);
                    conversations.update(state, "COLLECT", draft);
                    sender.send(chatId, "Пришлите фото/видео. По готовности нажмите кнопку.",
                            Keyboards.of("Медиа завершены", "MED_DONE"));
                } else if ("MED_DONE".equals(data)) {
                    conversations.complete(state);
                    sender.send(chatId, "Медиа сохранены.");
                }
            }
            default -> { }
        }
    }

    public void handleText(ConversationState state, String text, AuthenticatedActor actor) {
        Map<String, String> draft = conversations.readDraft(state);
        long chatId = state.getChatId();
        UUID orderId = state.getRelatedOrderId();

        switch (state.getScenario()) {
            case DECLINE_REASON -> {
                orderService.declineByMaster(orderId, text, actor);
                conversations.complete(state);
                sender.send(chatId, "Отказ зафиксирован.");
            }
            case PRICE_DECLINE_REASON -> {
                orderService.declineByCustomer(orderId, text, actor);
                conversations.complete(state);
                sender.send(chatId, "Отказ клиента зафиксирован.");
            }
            case CANCEL_REASON -> {
                orderService.cancel(orderId, text, actor);
                conversations.complete(state);
                sender.send(chatId, "Заказ отменён.");
            }
            case UNREPAIRABLE_REASON -> {
                orderService.markUnrepairable(orderId, text, actor);
                conversations.complete(state);
                sender.send(chatId, "Зафиксировано: ремонт нецелесообразен.");
            }
            case WAITING_PART -> handleWaitingPartText(state, draft, text, chatId, orderId, actor);
            case RESCHEDULE -> handleRescheduleText(state, draft, text, chatId, actor);
            case WARRANTY -> handleWarrantyText(state, draft, text, chatId, actor);
            case PRICE_APPROVAL_INPUT -> handlePriceApprovalText(state, draft, text, chatId, orderId, actor);
            case SEARCH -> {
                List<Order> results = orderService.search(text, actor);
                conversations.complete(state);
                if (results.isEmpty()) {
                    sender.send(chatId, "Ничего не найдено.");
                } else {
                    for (Order order : results) {
                        sender.send(chatId, presenter.renderCard(order), presenter.actionsFor(order, actor));
                    }
                }
            }
            default -> sender.send(chatId, "Ожидается выбор кнопкой.");
        }
    }

    private void handleWaitingPartText(ConversationState state, Map<String, String> draft, String text,
                                        long chatId, UUID orderId, AuthenticatedActor actor) {
        switch (state.getStep()) {
            case "PART_NAME" -> {
                draft.put("partName", text);
                conversations.update(state, "PART_NUMBER", draft);
                sender.send(chatId, "Артикул (или '-'):");
            }
            case "PART_NUMBER" -> {
                draft.put("partNumber", "-".equals(text.trim()) ? null : text);
                conversations.update(state, "PART_COST", draft);
                sender.send(chatId, "Ориентировочная закупочная цена (или '-'):");
            }
            case "PART_COST" -> {
                BigDecimal cost = null;
                if (!"-".equals(text.trim())) {
                    try {
                        cost = new BigDecimal(text.trim().replace(",", "."));
                    } catch (NumberFormatException e) {
                        sender.send(chatId, "Введите число или '-':");
                        return;
                    }
                }
                draft.put("partCost", cost == null ? null : cost.toPlainString());
                conversations.update(state, "COMMENT", draft);
                sender.send(chatId, "Комментарий (или '-'):");
            }
            case "COMMENT" -> {
                String comment = "-".equals(text.trim()) ? null : text;
                WaitingPartCommand cmd = new WaitingPartCommand(draft.get("partName"), draft.get("partNumber"),
                        draft.get("partCost") != null ? new BigDecimal(draft.get("partCost")) : null, comment);
                orderService.markWaitingPart(orderId, cmd, actor);
                conversations.complete(state);
                sender.send(chatId, "Статус: нужна деталь. Администратор уведомлён.");
            }
            default -> { }
        }
    }

    private void handlePriceApprovalText(ConversationState state, Map<String, String> draft, String text,
                                          long chatId, UUID orderId, AuthenticatedActor actor) {
        switch (state.getStep()) {
            case "FAILURE_REASON" -> {
                draft.put("failureReason", text);
                conversations.update(state, "WORK_NEEDED", draft);
                sender.send(chatId, "Какие работы требуются:");
            }
            case "WORK_NEEDED" -> {
                draft.put("workNeeded", text);
                conversations.update(state, "LABOR_PRICE", draft);
                sender.send(chatId, "Стоимость работы (число):");
            }
            case "LABOR_PRICE" -> {
                BigDecimal labor = parseMoneyOrPrompt(text, chatId);
                if (labor == null) return;
                draft.put("laborPrice", labor.toPlainString());
                conversations.update(state, "PARTS_PRICE", draft);
                sender.send(chatId, "Цена запчастей для клиента (0, если нет):");
            }
            case "PARTS_PRICE" -> {
                BigDecimal parts = parseMoneyOrPrompt(text, chatId);
                if (parts == null) return;
                PriceApprovalCommand cmd = new PriceApprovalCommand(draft.get("failureReason"),
                        draft.get("workNeeded"), new BigDecimal(draft.get("laborPrice")), parts);
                Order order = orderService.startPriceApproval(orderId, cmd, actor);
                conversations.complete(state);
                sender.send(chatId, "Стоимость клиенту: работы %s ₽, запчасти %s ₽, итого %s ₽"
                        .formatted(order.getLaborPrice(), order.getPartsSellPrice(), order.getEstimatedPrice()));
                sender.send(chatId, presenter.renderCard(order), presenter.actionsFor(order, actor));
            }
            default -> { }
        }
    }

    private BigDecimal parseMoneyOrPrompt(String text, long chatId) {
        try {
            return new BigDecimal(text.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            sender.send(chatId, "Введите число, например 4500:");
            return null;
        }
    }

    private void handleRescheduleText(ConversationState state, Map<String, String> draft, String text, long chatId,
                                       AuthenticatedActor actor) {
        if ("DATE".equals(state.getStep())) {
            try {
                LocalDate date = LocalDate.parse(text.trim(), DATE_FMT);
                draft.put("visitDate", date.format(DATE_FMT));
                conversations.update(state, "SLOT", draft);
                sender.send(chatId, "Новый временной слот:", slotKeyboard());
            } catch (Exception e) {
                sender.send(chatId, "Формат ДД.ММ.ГГГГ, попробуйте снова:");
            }
        } else if ("REASON_TEXT".equals(state.getStep())) {
            finishReschedule(state, draft, text, actor);
        }
    }

    private void finishReschedule(ConversationState state, Map<String, String> draft, String reason, AuthenticatedActor actor) {
        RescheduleCommand cmd = new RescheduleCommand(
                LocalDate.parse(draft.get("visitDate"), DATE_FMT),
                LocalTime.parse(draft.get("timeFrom")),
                LocalTime.parse(draft.get("timeTo")),
                reason);
        orderService.reschedule(state.getRelatedOrderId(), cmd, actor);
        conversations.complete(state);
        sender.send(state.getChatId(), "Заказ перенесён.");
    }

    private void handleWarrantyText(ConversationState state, Map<String, String> draft, String text, long chatId,
                                     AuthenticatedActor actor) {
        if ("PROBLEM".equals(state.getStep())) {
            draft.put("problem", text);
            conversations.update(state, "DATE", draft);
            sender.send(chatId, "Дата визита (ДД.ММ.ГГГГ):");
        } else if ("DATE".equals(state.getStep())) {
            try {
                LocalDate date = LocalDate.parse(text.trim(), DATE_FMT);
                draft.put("visitDate", date.format(DATE_FMT));
                conversations.update(state, "SLOT", draft);
                sender.send(chatId, "Временной слот:", slotKeyboard());
            } catch (Exception e) {
                sender.send(chatId, "Формат ДД.ММ.ГГГГ, попробуйте снова:");
            }
        } else if ("COMMENT".equals(state.getStep())) {
            draft.put("comment", "-".equals(text.trim()) ? null : text);
            WarrantyCommand cmd = new WarrantyCommand(
                    state.getRelatedOrderId(),
                    draft.get("problem"),
                    LocalDate.parse(draft.get("visitDate"), DATE_FMT),
                    LocalTime.parse(draft.get("timeFrom")),
                    LocalTime.parse(draft.get("timeTo")),
                    draft.get("masterId") != null ? UUID.fromString(draft.get("masterId")) : null,
                    draft.get("comment"));
            var warrantyOrder = orderService.createWarrantyOrder(cmd, actor);
            conversations.complete(state);
            sender.send(chatId, "Гарантийный визит #%d создан.".formatted(warrantyOrder.getNumber()));
        }
    }

    private void showMasters(Map<String, String> draft, long chatId, AuthenticatedActor actor) {
        List<Master> suitable = masterService.findSuitable(draft.get("applianceType"), null, null, actor);
        List<String[]> options = new ArrayList<>();
        for (Master m : suitable) {
            options.add(new String[]{m.getName(), m.getId().toString()});
        }
        options.add(new String[]{"Без мастера", "NONE"});
        sender.send(chatId, "Мастер:", Keyboards.singleColumn(options));
    }

    private org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup reasonKeyboard(
            List<String> reasons, String prefix) {
        List<String[]> options = new ArrayList<>();
        for (int i = 0; i < reasons.size(); i++) {
            boolean isOther = i == reasons.size() - 1;
            options.add(new String[]{reasons.get(i), isOther ? prefix + ":OTHER" : prefix + ":" + i});
        }
        return Keyboards.singleColumn(options);
    }

    private org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup slotKeyboard() {
        List<String[]> options = catalog.getTimeSlots().stream().map(s -> new String[]{s, s}).toList();
        return Keyboards.grid(options, 2);
    }

    private org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup stageKeyboard() {
        List<String[]> options = new ArrayList<>();
        for (MediaStage stage : MediaStage.values()) {
            options.add(new String[]{stage.name(), stage.name()});
        }
        return Keyboards.grid(options, 3);
    }
}
