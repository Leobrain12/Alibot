package com.alibot.bot;

import com.alibot.config.AppProperties;
import com.alibot.config.BotConfiguredCondition;
import com.alibot.config.BotProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.api.methods.menubutton.SetChatMenuButton;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.api.objects.menubutton.MenuButtonWebApp;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * ТЗ п.93/94 — production предпочтительно webhook (с secret_token), polling допустим для
 * local/dev/staging. Режим переключается через bot.mode, оба варианта используют тот же
 * UpdateDispatcher (через AlibotTelegramBot либо WebhookController).
 */
@Component
@Conditional(BotConfiguredCondition.class)
@RequiredArgsConstructor
@Slf4j
public class BotBootstrap implements ApplicationRunner {

    private final BotProperties botProperties;
    private final AppProperties appProperties;
    private final TelegramClient telegramClient;
    private final AlibotTelegramBot bot;

    private TelegramBotsLongPollingApplication longPollingApp;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (botProperties.isWebhookMode()) {
            String url = botProperties.getWebhookBaseUrl() + "/telegram/webhook/" + botProperties.getWebhookSecretToken();
            telegramClient.execute(SetWebhook.builder()
                    .url(url)
                    .secretToken(botProperties.getWebhookSecretToken())
                    .build());
            log.info("Telegram webhook установлен: {}", url);
        } else {
            telegramClient.execute(DeleteWebhook.builder().build());
            longPollingApp = new TelegramBotsLongPollingApplication();
            longPollingApp.registerBot(botProperties.getToken(), bot);
            log.info("Telegram bot запущен в режиме long polling");
        }
        setupMiniAppMenuButton();
    }

    /** Если задан app.mini-app.base-url (обязательно HTTPS — иначе Telegram отклонит вызов),
     *  вешаем постоянную кнопку "Открыть Mini App" рядом с полем ввода — глобально для всех
     *  пользователей бота (chat_id не указан). Без этого свойства пропускаем — тогда Mini App
     *  всё ещё доступен через кнопку в /start (см. CommandRouter), если base-url появится позже. */
    private void setupMiniAppMenuButton() {
        String baseUrl = appProperties.getMiniApp().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            log.info("app.mini-app.base-url не задан — постоянная кнопка Mini App не настраивается");
            return;
        }
        try {
            telegramClient.execute(SetChatMenuButton.builder()
                    .menuButton(MenuButtonWebApp.builder()
                            .text("Открыть")
                            .webAppInfo(WebAppInfo.builder().url(baseUrl + "/miniapp/index.html").build())
                            .build())
                    .build());
            log.info("Menu Button Mini App настроена: {}/miniapp/index.html", baseUrl);
        } catch (Exception e) {
            log.warn("Не удалось настроить Menu Button (проверьте, что MINI_APP_BASE_URL — рабочий HTTPS-адрес): {}",
                    e.getMessage());
        }
    }

    /** ТЗ п.104 — состояние для health-индикатора: в polling-режиме проверяем реально ли жива
     *  long-polling сессия; в webhook-режиме факт того, что run() отработал без исключения
     *  (иначе Spring не поднялся бы), уже означает успешный SetWebhook. */
    public boolean isHealthy() {
        if (botProperties.isWebhookMode()) {
            return true;
        }
        return longPollingApp != null && longPollingApp.isRunning();
    }

    @PreDestroy
    public void shutdown() throws Exception {
        if (longPollingApp != null) {
            longPollingApp.close();
        }
    }
}
