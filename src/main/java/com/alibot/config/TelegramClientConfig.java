package com.alibot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Бот (и всё, что от него зависит) активируется, только если задан TELEGRAM_BOT_TOKEN — это
 * позволяет поднять REST API + Mini App для проверки без реального токена (см. BotConfiguredCondition).
 */
@Configuration
public class TelegramClientConfig {

    @Bean
    @Conditional(BotConfiguredCondition.class)
    public TelegramClient telegramClient(BotProperties botProperties) {
        return new OkHttpTelegramClient(TelegramHttpClientFactory.build(botProperties), botProperties.getToken());
    }
}
