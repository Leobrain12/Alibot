package com.alibot.bot;

import com.alibot.config.BotConfiguredCondition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/** Тонкая обвязка над TelegramClient — только транспорт (отправка сообщений), без бизнес-логики. */
@Component
@Conditional(BotConfiguredCondition.class)
@RequiredArgsConstructor
@Slf4j
public class BotSender {

    private final TelegramClient telegramClient;

    /** @return id отправленного сообщения (нужен, чтобы потом его можно было отредактировать
     *  вместо отправки нового — см. editOrSend), либо null, если отправка не удалась. */
    public Integer send(long chatId, String text) {
        return send(chatId, text, null);
    }

    public Integer send(long chatId, String text, ReplyKeyboard keyboard) {
        SendMessage.SendMessageBuilder<?, ?> builder = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML");
        if (keyboard != null) {
            builder.replyMarkup(keyboard);
        }
        try {
            Message sent = telegramClient.execute(builder.build());
            return sent.getMessageId();
        } catch (TelegramApiException e) {
            log.warn("Не удалось отправить сообщение в чат {}: {}", chatId, e.getMessage());
            return null;
        }
    }

    /** Правит текст и клавиатуру уже показанного сообщения вместо отправки нового — так при
     *  навигации по меню/спискам в чате не копится история из старых "страниц". Если правка не
     *  удалась (сообщение старше 48ч, удалено пользователем и т.п.) — откатывается на обычную
     *  отправку нового сообщения, чтобы пользователь в любом случае получил ответ.
     *  editMessageId == null — то же самое, что и обычная отправка (нечего редактировать,
     *  например самый первый /start в чате). */
    public Integer editOrSend(long chatId, Integer editMessageId, String text, InlineKeyboardMarkup keyboard) {
        if (editMessageId == null) {
            return send(chatId, text, keyboard);
        }
        EditMessageText.EditMessageTextBuilder<?, ?> builder = EditMessageText.builder()
                .chatId(chatId)
                .messageId(editMessageId)
                .text(text)
                .parseMode("HTML");
        if (keyboard != null) {
            builder.replyMarkup(keyboard);
        }
        try {
            telegramClient.execute(builder.build());
            return editMessageId;
        } catch (TelegramApiException e) {
            String message = e.getMessage();
            if (message != null && message.contains("message is not modified")) {
                return editMessageId;
            }
            log.debug("Не удалось отредактировать сообщение {} в чате {} (шлю новое): {}", editMessageId, chatId, message);
            return send(chatId, text, keyboard);
        }
    }

    public Integer editOrSend(long chatId, Integer editMessageId, String text) {
        return editOrSend(chatId, editMessageId, text, null);
    }

    public void answerCallback(String callbackQueryId, String text) {
        try {
            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQueryId)
                    .text(text)
                    .build());
        } catch (TelegramApiException e) {
            log.warn("Не удалось ответить на callback {}: {}", callbackQueryId, e.getMessage());
        }
    }
}
