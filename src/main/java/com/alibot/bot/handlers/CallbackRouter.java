package com.alibot.bot.handlers;

import com.alibot.bot.BotSender;
import com.alibot.bot.OrderPresenter;
import com.alibot.bot.keyboard.Keyboards;
import com.alibot.config.BotConfiguredCondition;
import com.alibot.domain.ConversationState;
import com.alibot.domain.ContactResult;
import com.alibot.domain.Master;
import com.alibot.domain.MediaStage;
import com.alibot.domain.Order;
import com.alibot.domain.OrderStatus;
import com.alibot.service.AuthenticatedActor;
import com.alibot.service.ContactAttemptService;
import com.alibot.service.ConversationStateService;
import com.alibot.service.MasterService;
import com.alibot.service.OrderService;
import com.alibot.service.PaymentService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

/**
 * Роутинг callback_data кнопок. Транспортная развязка: разбирает строку вида "ACC:<orderId>" и
 * вызывает соответствующий метод OrderService/PaymentService/ContactAttemptService — сам не
 * содержит правил валидности переходов или прав (см. OrderStatusMachine/AccessControlService).
 */
@Component
@Conditional(BotConfiguredCondition.class)
@RequiredArgsConstructor
public class CallbackRouter {

    private final ConversationStateService conversations;
    private final CreateOrderWizard createOrderWizard;
    private final WorkReportWizard workReportWizard;
    private final MiscWizards miscWizards;
    private final CommandRouter commandRouter;
    private final OrderService orderService;
    private final MasterService masterService;
    private final PaymentService paymentService;
    private final ContactAttemptService contactAttemptService;
    private final OrderPresenter presenter;
    private final BotSender sender;

    /** Префиксы callback_data кнопок на карточках заказов/меню (см. switch ниже) — эти кнопки
     *  могут остаться на экране под старым сообщением, пока в этом же чате идёт визард. Раньше
     *  ЛЮБОЙ callback при активном визарде безусловно уходил в его handleCallback, даже если
     *  data явно не принадлежала шагу визарда (шаги визардов никогда не используют эти префиксы,
     *  их callback_data — просто значение опции без ":"). Нажатие такой залежавшейся кнопки
     *  (например "Отменить" на другом заказе) подсовывало визарду мусорные данные вместо
     *  ожидаемого шага, тот падал с исключением на середине, а ConversationState оставался
     *  висеть "активным" ещё до 30 минут (app.conversation.timeout-minutes) — все последующие
     *  нажатия в чате в это время уходили туда же, а не выполняли реальное действие. */
    private static final Set<String> STANDALONE_ORDER_ACTIONS = Set.of(
            "ACC", "DEC", "OTW", "ARR", "DIAG", "DGR", "PRICE_OK", "PRICE_NO", "NC", "NCOK",
            "RESCH", "RPV", "MED", "REPORT", "CHM", "ASSIGN", "CANCELORD", "WARR", "PAYFULL", "ORDER",
            "MENU_NEW_ORDER", "MENU_ACTIVE", "MENU_UNASSIGNED", "MENU_LEADS", "MENU_HISTORY", "MENU_STATS",
            "MENU_MASTERS", "MENU_SEARCH", "STATS");

    public void handle(CallbackQuery cq, AuthenticatedActor actor) {
        long chatId = cq.getMessage().getChatId();
        long telegramUserId = cq.getFrom().getId();
        String data = cq.getData();
        sender.answerCallback(cq.getId(), "");

        boolean looksLikeStandaloneAction = STANDALONE_ORDER_ACTIONS.contains(data.split(":")[0]);
        Optional<ConversationState> active = looksLikeStandaloneAction ? Optional.empty() : conversations.findActive(chatId);
        if (active.isPresent()) {
            ConversationState state = active.get();
            switch (state.getScenario()) {
                case CreateOrderWizard.SCENARIO -> {
                    createOrderWizard.handleCallback(state, data, actor);
                    return;
                }
                case WorkReportWizard.SCENARIO -> {
                    workReportWizard.handleCallback(state, data, actor);
                    return;
                }
                case MiscWizards.DECLINE_REASON, MiscWizards.PRICE_DECLINE_REASON, MiscWizards.RESCHEDULE,
                     MiscWizards.WARRANTY, MiscWizards.MEDIA -> {
                    miscWizards.handleCallback(state, data, actor);
                    return;
                }
                default -> { /* нет активного шага, ожидающего callback — обрабатываем как обычное действие */ }
            }
        }

        String[] parts = data.split(":");
        String action = parts[0];

        switch (action) {
            case "ACC" -> act(parts[1], chatId, actor, id -> orderService.acceptByMaster(id, actor));
            case "DEC" -> miscWizards.startDeclineReason(UUID.fromString(parts[1]), chatId, telegramUserId);
            case "OTW" -> act(parts[1], chatId, actor, id -> orderService.markOnTheWay(id, actor));
            case "ARR" -> act(parts[1], chatId, actor, id -> orderService.markArrived(id, actor));
            case "DIAG" -> act(parts[1], chatId, actor, id -> orderService.startDiagnostics(id, actor));
            case "DGR" -> handleDiagnosisResult(parts, chatId, telegramUserId, actor);
            case "PRICE_OK" -> act(parts[1], chatId, actor, id -> orderService.approvePriceByCustomer(id, actor));
            case "PRICE_NO" -> miscWizards.startPriceDeclineReason(UUID.fromString(parts[1]), chatId, telegramUserId);
            case "NC" -> {
                contactAttemptService.recordAttempt(UUID.fromString(parts[1]), ContactResult.NO_ANSWER, null, actor);
                sender.send(chatId, "Недозвон зафиксирован.");
            }
            case "NCOK" -> act(parts[1], chatId, actor, id -> orderService.transitionSimple(id, OrderStatus.ACCEPTED, actor, "Связь восстановлена"));
            case "RESCH" -> miscWizards.startReschedule(UUID.fromString(parts[1]), chatId, telegramUserId);
            case "RPV" -> act(parts[1], chatId, actor, id -> orderService.transitionSimple(id, OrderStatus.ON_THE_WAY, actor, "Деталь получена, повторный визит"));
            case "MED" -> miscWizards.startMedia(UUID.fromString(parts[1]), chatId, telegramUserId);
            case "REPORT" -> workReportWizard.start(UUID.fromString(parts[1]), chatId, telegramUserId);
            case "CHM" -> showMasterPicker(UUID.fromString(parts[1]), chatId, actor);
            case "ASSIGN" -> {
                Order updated = orderService.changeMaster(UUID.fromString(parts[1]), UUID.fromString(parts[2]), actor);
                renderOrder(chatId, updated, actor);
            }
            case "CANCELORD" -> miscWizards.startCancel(UUID.fromString(parts[1]), chatId, telegramUserId);
            case "WARR" -> miscWizards.startWarranty(UUID.fromString(parts[1]), chatId, telegramUserId);
            case "PAYFULL" -> {
                paymentService.payFull(UUID.fromString(parts[1]), actor);
                sender.send(chatId, "Оплата зафиксирована.");
            }
            case "ORDER" -> {
                Order order = orderService.getById(UUID.fromString(parts[1]), actor);
                renderOrder(chatId, order, actor);
            }
            case "MENU_NEW_ORDER" -> {
                // Тот же guard, что и у команды /new_order (CommandRouter) — раньше здесь его
                // не было вовсе, и не-админ мог пройти весь 12-шаговый визард и получить отказ
                // только на самом последнем шаге, в OrderService.create().
                if (!actor.isAdmin()) {
                    sender.send(chatId, "Действие доступно только администратору.");
                } else {
                    createOrderWizard.start(chatId, telegramUserId);
                }
            }
            case "MENU_ACTIVE" -> commandRouter.sendActiveList(chatId, actor);
            case "MENU_UNASSIGNED" -> commandRouter.sendUnassignedList(chatId, actor);
            case "MENU_LEADS" -> commandRouter.sendLeadsList(chatId, actor);
            case "MENU_HISTORY" -> commandRouter.sendHistoryList(chatId, actor);
            case "MENU_STATS" -> commandRouter.sendStats(chatId, actor);
            case "STATS" -> commandRouter.sendStats(chatId, actor, parts[1]);
            case "MENU_MASTERS" -> sendMastersList(chatId, actor);
            case "MENU_SEARCH" -> miscWizards.startSearch(chatId, telegramUserId);
            default -> sender.send(chatId, "Действие не распознано.");
        }
    }

    private void handleDiagnosisResult(String[] parts, long chatId, long telegramUserId, AuthenticatedActor actor) {
        UUID orderId = UUID.fromString(parts[1]);
        String outcome = parts[2];
        switch (outcome) {
            case "REPAIR" -> miscWizards.startPriceApprovalInput(orderId, chatId, telegramUserId);
            case "PART" -> miscWizards.startWaitingPart(orderId, chatId, telegramUserId);
            case "UNREP" -> miscWizards.startUnrepairable(orderId, chatId, telegramUserId);
            case "CANCEL" -> miscWizards.startPriceDeclineReason(orderId, chatId, telegramUserId);
            default -> { }
        }
    }

    private void showMasterPicker(UUID orderId, long chatId, AuthenticatedActor actor) {
        Order order = orderService.getById(orderId, actor);
        List<Master> suitable = masterService.findSuitable(order.getApplianceType(), order.getBrand(), null, actor);
        List<String[]> options = new ArrayList<>();
        for (Master m : suitable) {
            options.add(new String[]{m.getName(), "ASSIGN:" + orderId + ":" + m.getId()});
        }
        sender.send(chatId, "Выберите мастера:", Keyboards.singleColumn(options));
    }

    private void sendMastersList(long chatId, AuthenticatedActor actor) {
        List<Master> masters = masterService.list(actor);
        StringBuilder sb = new StringBuilder("Мастера:\n");
        for (Master m : masters) {
            sb.append("%s — %s, сегодня: %s\n".formatted(m.getName(), m.getStatus(),
                    m.isActive() ? "активен" : "неактивен"));
        }
        sender.send(chatId, sb.toString());
    }

    private void renderOrder(long chatId, Order order, AuthenticatedActor actor) {
        sender.send(chatId, presenter.renderCard(order), presenter.actionsFor(order, actor));
    }

    private interface OrderAction {
        Order apply(UUID orderId);
    }

    private void act(String orderIdStr, long chatId, AuthenticatedActor actor, OrderAction action) {
        Order updated = action.apply(UUID.fromString(orderIdStr));
        renderOrder(chatId, updated, actor);
    }
}
