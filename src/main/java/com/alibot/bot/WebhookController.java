package com.alibot.bot;

import com.alibot.config.BotConfiguredCondition;
import com.alibot.config.BotProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * ТЗ п.93 — production webhook с проверкой secret_token в заголовке X-Telegram-Bot-Api-Secret-Token.
 * Активен только в bot.mode=webhook (в polling-режиме Telegram сюда ничего не шлёт).
 */
@RestController
@Conditional(BotConfiguredCondition.class)
@RequiredArgsConstructor
public class WebhookController {

    private final UpdateDispatcher dispatcher;
    private final BotProperties botProperties;

    @PostMapping("/telegram/webhook/{secret}")
    public ResponseEntity<Void> receive(@PathVariable String secret,
                                         @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String headerSecret,
                                         @RequestBody Update update) {
        String expected = botProperties.getWebhookSecretToken();
        boolean valid = expected != null && expected.equals(secret) && expected.equals(headerSecret);
        if (!valid) {
            return ResponseEntity.status(401).build();
        }
        dispatcher.dispatch(update);
        return ResponseEntity.ok().build();
    }
}
