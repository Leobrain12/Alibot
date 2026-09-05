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
import com.alibot.domain.ReferenceCategory;
import com.alibot.domain.ReferenceItem;
import com.alibot.domain.User;
import com.alibot.service.AuthenticatedActor;
import com.alibot.service.ContactAttemptService;
import com.alibot.service.ConversationStateService;
import com.alibot.service.MasterService;
import com.alibot.service.OrderService;
import com.alibot.service.PaymentService;
import com.alibot.service.ReferenceDataService;
import com.alibot.service.UserManagementService;
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
    private final ReferenceDataService referenceDataService;
    private final UserManagementService userManagementService;
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
            "MENU_MAIN", "MENU_NEW_ORDER", "MENU_ACTIVE", "MENU_UNASSIGNED", "MENU_LEADS", "MENU_HISTORY",
            "MENU_STATS", "MENU_MASTERS", "MENU_SEARCH", "STATS", "MENU_REFERENCE", "REFCAT", "REFTOG", "REFADD",
            "MENU_USERS", "USERTOG", "USERADD");

    public void handle(CallbackQuery cq, AuthenticatedActor actor) {
        long chatId = cq.getMessage().getChatId();
        long telegramUserId = cq.getFrom().getId();
        String data = cq.getData();
        // Сообщение с нажатой кнопкой — его правим на месте вместо отправки нового (см.
        // BotSender.editOrSend), чтобы навигация по меню/спискам не копилась в чате.
        Integer messageId = cq.getMessage().getMessageId();
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
                     MiscWizards.WARRANTY, MiscWizards.MEDIA, MiscWizards.USER_ADD -> {
                    miscWizards.handleCallback(state, data, actor);
                    return;
                }
                default -> { /* нет активного шага, ожидающего callback — обрабатываем как обычное действие */ }
            }
        }

        String[] parts = data.split(":");
        String action = parts[0];

        switch (action) {
            case "ACC" -> act(parts[1], chatId, messageId, actor, id -> orderService.acceptByMaster(id, actor));
            case "DEC" -> miscWizards.startDeclineReason(UUID.fromString(parts[1]), chatId, telegramUserId, messageId);
            case "OTW" -> act(parts[1], chatId, messageId, actor, id -> orderService.markOnTheWay(id, actor));
            case "ARR" -> act(parts[1], chatId, messageId, actor, id -> orderService.markArrived(id, actor));
            case "DIAG" -> act(parts[1], chatId, messageId, actor, id -> orderService.startDiagnostics(id, actor));
            case "DGR" -> handleDiagnosisResult(parts, chatId, telegramUserId, messageId, actor);
            case "PRICE_OK" -> act(parts[1], chatId, messageId, actor, id -> orderService.approvePriceByCustomer(id, actor));
            case "PRICE_NO" -> miscWizards.startPriceDeclineReason(UUID.fromString(parts[1]), chatId, telegramUserId, messageId);
            case "NC" -> {
                contactAttemptService.recordAttempt(UUID.fromString(parts[1]), ContactResult.NO_ANSWER, null, actor);
                sender.editOrSend(chatId, messageId, "Недозвон зафиксирован.");
            }
            case "NCOK" -> act(parts[1], chatId, messageId, actor, id -> orderService.transitionSimple(id, OrderStatus.ACCEPTED, actor, "Связь восстановлена"));
            case "RESCH" -> miscWizards.startReschedule(UUID.fromString(parts[1]), chatId, telegramUserId, messageId);
            case "RPV" -> act(parts[1], chatId, messageId, actor, id -> orderService.transitionSimple(id, OrderStatus.ON_THE_WAY, actor, "Деталь получена, повторный визит"));
            case "MED" -> miscWizards.startMedia(UUID.fromString(parts[1]), chatId, telegramUserId, messageId);
            case "REPORT" -> workReportWizard.start(UUID.fromString(parts[1]), chatId, telegramUserId, messageId);
            case "CHM" -> showMasterPicker(UUID.fromString(parts[1]), chatId, messageId, actor);
            case "ASSIGN" -> {
                Order updated = orderService.changeMaster(UUID.fromString(parts[1]), UUID.fromString(parts[2]), actor);
                renderOrder(chatId, messageId, updated, actor);
            }
            case "CANCELORD" -> miscWizards.startCancel(UUID.fromString(parts[1]), chatId, telegramUserId, messageId);
            case "WARR" -> miscWizards.startWarranty(UUID.fromString(parts[1]), chatId, telegramUserId, messageId);
            case "PAYFULL" -> {
                paymentService.payFull(UUID.fromString(parts[1]), actor);
                sender.editOrSend(chatId, messageId, "Оплата зафиксирована.");
            }
            case "ORDER" -> {
                Order order = orderService.getById(UUID.fromString(parts[1]), actor);
                renderOrder(chatId, messageId, order, actor);
            }
            case "MENU_NEW_ORDER" -> {
                // Тот же guard, что и у команды /new_order (CommandRouter) — раньше здесь его
                // не было вовсе, и не-админ мог пройти весь 12-шаговый визард и получить отказ
                // только на самом последнем шаге, в OrderService.create().
                if (!actor.isAdmin()) {
                    sender.editOrSend(chatId, messageId, "Действие доступно только администратору.");
                } else {
                    createOrderWizard.start(chatId, telegramUserId, messageId);
                }
            }
            case "MENU_MAIN" -> commandRouter.sendMainMenu(chatId, messageId, actor);
            case "MENU_ACTIVE" -> commandRouter.sendActiveList(chatId, messageId, actor);
            case "MENU_UNASSIGNED" -> commandRouter.sendUnassignedList(chatId, messageId, actor);
            case "MENU_LEADS" -> commandRouter.sendLeadsList(chatId, messageId, actor);
            case "MENU_HISTORY" -> commandRouter.sendHistoryList(chatId, messageId, actor);
            case "MENU_STATS" -> commandRouter.sendStats(chatId, messageId, actor);
            case "STATS" -> commandRouter.sendStats(chatId, messageId, actor, parts[1]);
            case "MENU_MASTERS" -> sendMastersList(chatId, messageId, actor);
            case "MENU_SEARCH" -> miscWizards.startSearch(chatId, telegramUserId, messageId);
            case "MENU_REFERENCE" -> commandRouter.sendReferenceCategories(chatId, messageId, actor);
            case "REFCAT" -> commandRouter.sendReferenceItems(chatId, messageId, ReferenceCategory.valueOf(parts[1]), actor);
            case "REFTOG" -> {
                ReferenceItem updated = referenceDataService.update(
                        UUID.fromString(parts[1]), null, Boolean.parseBoolean(parts[2]), null, actor);
                commandRouter.sendReferenceItems(chatId, messageId, updated.getCategory(), actor);
            }
            case "REFADD" -> miscWizards.startReferenceAdd(ReferenceCategory.valueOf(parts[1]), chatId, telegramUserId, messageId);
            case "MENU_USERS" -> commandRouter.sendUsersList(chatId, messageId, actor);
            case "USERTOG" -> {
                userManagementService.setActive(UUID.fromString(parts[1]), Boolean.parseBoolean(parts[2]), actor);
                commandRouter.sendUsersList(chatId, messageId, actor);
            }
            case "USERADD" -> miscWizards.startUserAdd(chatId, telegramUserId, messageId);
            default -> sender.editOrSend(chatId, messageId, "Действие не распознано.");
        }
    }

    private void handleDiagnosisResult(String[] parts, long chatId, long telegramUserId, Integer messageId, AuthenticatedActor actor) {
        UUID orderId = UUID.fromString(parts[1]);
        String outcome = parts[2];
        switch (outcome) {
            case "REPAIR" -> miscWizards.startPriceApprovalInput(orderId, chatId, telegramUserId, messageId);
            case "PART" -> miscWizards.startWaitingPart(orderId, chatId, telegramUserId, messageId);
            case "UNREP" -> miscWizards.startUnrepairable(orderId, chatId, telegramUserId, messageId);
            case "CANCEL" -> miscWizards.startPriceDeclineReason(orderId, chatId, telegramUserId, messageId);
            default -> { }
        }
    }

    private void showMasterPicker(UUID orderId, long chatId, Integer messageId, AuthenticatedActor actor) {
        Order order = orderService.getById(orderId, actor);
        List<Master> suitable = masterService.findSuitable(order.getApplianceType(), order.getBrand(), null, actor);
        List<String[]> options = new ArrayList<>();
        for (Master m : suitable) {
            options.add(new String[]{m.getName(), "ASSIGN:" + orderId + ":" + m.getId()});
        }
        sender.editOrSend(chatId, messageId, "Выберите мастера:",
                Keyboards.withBack(Keyboards.singleColumn(options), "ORDER:" + orderId));
    }

    private void sendMastersList(long chatId, Integer messageId, AuthenticatedActor actor) {
        List<Master> masters = masterService.list(actor);
        StringBuilder sb = new StringBuilder("Мастера:\n");
        for (Master m : masters) {
            sb.append("%s — %s, сегодня: %s\n".formatted(m.getName(), m.getStatus(),
                    m.isActive() ? "активен" : "неактивен"));
        }
        sender.editOrSend(chatId, messageId, sb.toString(), Keyboards.withBack(Keyboards.of(), "MENU_MAIN"));
    }

    private void renderOrder(long chatId, Integer messageId, Order order, AuthenticatedActor actor) {
        sender.editOrSend(chatId, messageId, presenter.renderCard(order), presenter.actionsFor(order, actor));
    }

    private interface OrderAction {
        Order apply(UUID orderId);
    }

    private void act(String orderIdStr, long chatId, Integer messageId, AuthenticatedActor actor, OrderAction action) {
        Order updated = action.apply(UUID.fromString(orderIdStr));
        renderOrder(chatId, messageId, updated, actor);
    }
}
