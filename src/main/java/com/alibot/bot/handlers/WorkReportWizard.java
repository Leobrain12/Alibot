package com.alibot.bot.handlers;

import com.alibot.bot.BotSender;
import com.alibot.bot.keyboard.Keyboards;
import com.alibot.config.BotConfiguredCondition;
import com.alibot.domain.CommissionType;
import com.alibot.domain.ConversationState;
import com.alibot.domain.Order;
import com.alibot.service.AuthenticatedActor;
import com.alibot.service.ConversationStateService;
import com.alibot.service.MediaService;
import com.alibot.service.OrderService;
import com.alibot.service.WorkReportService;
import com.alibot.service.dto.WorkReportCommand;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/** ТЗ п.39-46/56-58 — обязательный отчёт мастера при завершении ремонта. */
@Component
@Conditional(BotConfiguredCondition.class)
@RequiredArgsConstructor
public class WorkReportWizard {

    public static final String SCENARIO = "WORK_REPORT";

    private final ConversationStateService conversations;
    private final OrderService orderService;
    private final WorkReportService workReportService;
    private final MediaService mediaService;
    private final BotSender sender;

    public void start(UUID orderId, long chatId, long telegramUserId, Integer editMessageId) {
        ConversationState state = conversations.start(chatId, telegramUserId, SCENARIO, "DESCRIPTION", orderId);
        sender.editOrSend(chatId, editMessageId, "Опишите, что было сделано:");
    }

    public void handleText(ConversationState state, String text, AuthenticatedActor actor) {
        Map<String, String> draft = conversations.readDraft(state);
        long chatId = state.getChatId();

        switch (state.getStep()) {
            case "DESCRIPTION" -> {
                draft.put("workDescription", text);
                conversations.update(state, "LABOR", draft);
                sender.send(chatId, "Стоимость работ для клиента (число):");
            }
            case "LABOR" -> {
                BigDecimal labor = parseMoney(text, chatId);
                if (labor == null) return;
                draft.put("laborPrice", labor.toPlainString());
                conversations.update(state, "PARTS", draft);
                sender.send(chatId, "Стоимость запчастей для клиента (0, если нет):");
            }
            case "PARTS" -> {
                BigDecimal parts = parseMoney(text, chatId);
                if (parts == null) return;
                draft.put("partsSellPrice", parts.toPlainString());
                conversations.update(state, "PARTS_COST", draft);
                sender.send(chatId, "Себестоимость запчастей (0, если нет):");
            }
            case "PARTS_COST" -> {
                BigDecimal cost = parseMoney(text, chatId);
                if (cost == null) return;
                draft.put("partsCost", cost.toPlainString());
                advancePastCost(state, draft, chatId, actor);
            }
            case "PAYOUT" -> {
                BigDecimal payout = parseMoney(text, chatId);
                if (payout == null) return;
                draft.put("masterPayout", payout.toPlainString());
                conversations.update(state, "MEDIA", draft);
                promptMedia(chatId);
            }
            default -> sender.send(chatId, "Сейчас ожидается действие кнопкой.");
        }
    }

    public void handleCallback(ConversationState state, String data, AuthenticatedActor actor) {
        Map<String, String> draft = conversations.readDraft(state);
        long chatId = state.getChatId();

        if ("WR_MEDIA_DONE".equals(data) && "MEDIA".equals(state.getStep())) {
            conversations.update(state, "CONFIRM", draft);
            sendConfirmation(state.getRelatedOrderId(), draft, chatId, actor);
        } else if ("WR_CONFIRM".equals(data) && "CONFIRM".equals(state.getStep())) {
            WorkReportCommand cmd = new WorkReportCommand(
                    draft.get("workDescription"),
                    new BigDecimal(draft.get("laborPrice")),
                    new BigDecimal(draft.get("partsSellPrice")),
                    new BigDecimal(draft.get("partsCost")),
                    draft.containsKey("masterPayout") ? new BigDecimal(draft.get("masterPayout")) : null,
                    null);
            workReportService.submit(state.getRelatedOrderId(), cmd, actor);
            conversations.complete(state);
            sender.send(chatId, "Заказ завершён, отчёт сохранён.");
        }
    }

    private void advancePastCost(ConversationState state, Map<String, String> draft, long chatId, AuthenticatedActor actor) {
        Order order = orderService.getById(state.getRelatedOrderId(), actor);
        if (order.getMaster().getCommissionType() == CommissionType.MANUAL) {
            conversations.update(state, "PAYOUT", draft);
            sender.send(chatId, "Выплата мастеру (число):");
        } else {
            conversations.update(state, "MEDIA", draft);
            promptMedia(chatId);
        }
    }

    private void promptMedia(long chatId) {
        sender.send(chatId, "Добавьте фото и видео ремонта. Рекомендуется приложить: общий вид техники, "
                + "неисправный узел, установленную деталь, результат после ремонта.",
                Keyboards.of("Медиа завершены", "WR_MEDIA_DONE"));
    }

    private void sendConfirmation(UUID orderId, Map<String, String> draft, long chatId, AuthenticatedActor actor) {
        long photos = mediaService.countPhotos(orderId);
        long videos = mediaService.countVideos(orderId);
        BigDecimal total = new BigDecimal(draft.get("laborPrice")).add(new BigDecimal(draft.get("partsSellPrice")));
        String text = """
                Что сделано: %s
                Стоимость работ: %s ₽
                Запчасти: %s ₽
                Итого: %s ₽
                Фото: %d, Видео: %d
                """.formatted(draft.get("workDescription"), draft.get("laborPrice"), draft.get("partsSellPrice"),
                total, photos, videos);
        sender.send(chatId, text, Keyboards.of("Подтвердить завершение", "WR_CONFIRM"));
    }

    private BigDecimal parseMoney(String text, long chatId) {
        try {
            return new BigDecimal(text.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            sender.send(chatId, "Введите число, например 4500:");
            return null;
        }
    }
}
