package com.alibot.bot;

import com.alibot.bot.handlers.CallbackRouter;
import com.alibot.bot.handlers.CommandRouter;
import com.alibot.bot.handlers.CreateOrderWizard;
import com.alibot.bot.handlers.MediaUploadHandler;
import com.alibot.bot.handlers.MiscWizards;
import com.alibot.bot.handlers.WorkReportWizard;
import com.alibot.config.BotConfiguredCondition;
import com.alibot.domain.ConversationState;
import com.alibot.domain.MediaStage;
import com.alibot.service.ActorResolver;
import com.alibot.service.AuthenticatedActor;
import com.alibot.service.ConversationStateService;
import com.alibot.service.IdempotencyService;
import com.alibot.service.exception.ForbiddenException;
import com.alibot.service.exception.InvalidTransitionException;
import com.alibot.service.exception.NotFoundException;
import com.alibot.service.exception.StaleOrderStateException;
import com.alibot.service.exception.ValidationException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

/**
 * Единая точка входа для всех Telegram Update — и из long polling (AlibotTelegramBot), и из
 * webhook (WebhookController). ТЗ п.90 — идемпотентность проверяется здесь до какой-либо
 * обработки. Дальше только маршрутизация: сам класс не принимает бизнес-решений — все действия
 * идут через OrderService и другие сервисы (см. CommandRouter/CallbackRouter/*Wizard).
 */
@Component
@Conditional(BotConfiguredCondition.class)
@RequiredArgsConstructor
@Slf4j
public class UpdateDispatcher {

    private final IdempotencyService idempotencyService;
    private final ActorResolver actorResolver;
    private final ConversationStateService conversations;
    private final CommandRouter commandRouter;
    private final CallbackRouter callbackRouter;
    private final CreateOrderWizard createOrderWizard;
    private final WorkReportWizard workReportWizard;
    private final MiscWizards miscWizards;
    private final MediaUploadHandler mediaUploadHandler;
    private final BotSender sender;

    public void dispatch(Update update) {
        if (!idempotencyService.markIfFirstTime(update.getUpdateId().longValue())) {
            log.debug("Повторный update {} — пропускаем", update.getUpdateId());
            return;
        }
        Long chatId = update.hasCallbackQuery() ? update.getCallbackQuery().getMessage().getChatId()
                : update.hasMessage() ? update.getMessage().getChatId() : null;
        try {
            if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
            } else if (update.hasMessage()) {
                handleMessage(update.getMessage());
            }
        } catch (NotFoundException | ForbiddenException | ValidationException
                 | InvalidTransitionException | StaleOrderStateException e) {
            // Ожидаемые доменные ошибки (ТЗ п.112 и т.п.) — показываем пользователю причину.
            log.info("Отклонено при обработке update {}: {}", update.getUpdateId(), e.getMessage());
            if (chatId != null) {
                sender.send(chatId, "⚠ " + e.getMessage());
            }
        } catch (OptimisticLockingFailureException e) {
            // ТЗ п.112 — заказ параллельно изменил кто-то другой (например, админ переназначил
            // заказ, пока мастер жал "Принять" по старой версии).
            log.info("Конфликт версий при обработке update {}: {}", update.getUpdateId(), e.getMessage());
            if (chatId != null) {
                sender.send(chatId, "⚠ Заявка уже изменена администратором. Обновите список заказов.");
            }
        } catch (Exception e) {
            log.error("Ошибка обработки update {}", update.getUpdateId(), e);
            if (chatId != null) {
                sender.send(chatId, "Произошла ошибка при обработке действия. Попробуйте ещё раз или начните заново: /start");
            }
        }
    }

    private void handleCallback(CallbackQuery cq) {
        Optional<AuthenticatedActor> actor = actorResolver.resolve(cq.getFrom().getId());
        if (actor.isEmpty()) {
            sender.answerCallback(cq.getId(), "Доступ не предоставлен. Обратитесь к администратору.");
            return;
        }
        callbackRouter.handle(cq, actor.get());
    }

    private void handleMessage(Message message) {
        Long chatId = message.getChatId();
        Long telegramUserId = message.getFrom() != null ? message.getFrom().getId() : null;
        if (telegramUserId == null) {
            return;
        }
        Optional<AuthenticatedActor> actorOpt = actorResolver.resolve(telegramUserId);
        if (actorOpt.isEmpty()) {
            sender.send(chatId, "Доступ к системе не предоставлен. Обратитесь к администратору.");
            return;
        }
        AuthenticatedActor actor = actorOpt.get();

        if (message.hasPhoto() || message.hasVideo()) {
            handleMedia(message, actor);
            return;
        }
        if (!message.hasText()) {
            return;
        }
        String text = message.getText();
        if (text.startsWith("/")) {
            commandRouter.handle(text, chatId, telegramUserId, actor);
            return;
        }

        Optional<ConversationState> active = conversations.findActive(chatId);
        if (active.isEmpty()) {
            commandRouter.sendMainMenu(chatId, null, actor);
            return;
        }
        ConversationState state = active.get();
        switch (state.getScenario()) {
            case CreateOrderWizard.SCENARIO -> createOrderWizard.handleText(state, text, actor);
            case WorkReportWizard.SCENARIO -> workReportWizard.handleText(state, text, actor);
            default -> miscWizards.handleText(state, text, actor);
        }
    }

    private void handleMedia(Message message, AuthenticatedActor actor) {
        long chatId = message.getChatId();
        Optional<ConversationState> active = conversations.findActive(chatId);
        if (active.isEmpty() || active.get().getRelatedOrderId() == null) {
            sender.send(chatId, "Сейчас медиа не ожидается. Откройте заказ и нажмите «Добавить медиа».");
            return;
        }
        ConversationState state = active.get();
        MediaStage stage;
        if (MiscWizards.MEDIA.equals(state.getScenario()) && "COLLECT".equals(state.getStep())) {
            stage = MediaStage.valueOf(conversations.readDraft(state).getOrDefault("stage", "OTHER"));
        } else if (WorkReportWizard.SCENARIO.equals(state.getScenario()) && "MEDIA".equals(state.getStep())) {
            stage = MediaStage.AFTER;
        } else {
            sender.send(chatId, "Сейчас медиа не ожидается.");
            return;
        }
        mediaUploadHandler.handle(message, stage, state.getRelatedOrderId(), actor);
    }
}
