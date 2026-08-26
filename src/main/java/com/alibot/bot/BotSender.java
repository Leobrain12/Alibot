package com.alibot.bot;

import com.alibot.config.BotConfiguredCondition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
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

    public void send(long chatId, String text) {
        send(chatId, text, null);
    }

    public void send(long chatId, String text, ReplyKeyboard keyboard) {
        SendMessage.SendMessageBuilder<?, ?> builder = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML");
        if (keyboard != null) {
            builder.replyMarkup(keyboard);
        }
        try {
            telegramClient.execute(builder.build());
        } catch (TelegramApiException e) {
            log.warn("Не удалось отправить сообщение в чат {}: {}", chatId, e.getMessage());
        }
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
