package com.alibot.config;

import java.net.InetSocketAddress;
import java.net.Proxy;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

/**
 * Некоторые хостинги режут исходящий TCP к серверам Telegram (DNS резолвится, TCP-подключение
 * висит по таймауту, хотя остальной интернет работает нормально — воспроизведено на практике на
 * одном из VPS). И разовые вызовы API (OkHttpTelegramClient), и сама long-polling сессия
 * (TelegramBotsLongPollingApplication) используют OkHttpClient независимо друг от друга — оба
 * места должны получить один и тот же прокси, иначе часть трафика всё равно упрётся в блокировку.
 * Без настроенного прокси собирает клиент ровно с теми же дефолтами, что и сама библиотека
 * (голый new OkHttpClient.Builder().build() — см. OkHttpTelegramClient(String) в telegrambots 9.0.0).
 */
@Slf4j
public final class TelegramHttpClientFactory {

    private TelegramHttpClientFactory() {
    }

    public static OkHttpClient build(BotProperties botProperties) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        if (botProperties.isProxyConfigured()) {
            Proxy.Type type = "SOCKS".equalsIgnoreCase(botProperties.getProxyType()) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
            builder.proxy(new Proxy(type, new InetSocketAddress(botProperties.getProxyHost(), botProperties.getProxyPort())));
            log.info("Исходящие запросы к Telegram API идут через {}-прокси {}:{}",
                    type, botProperties.getProxyHost(), botProperties.getProxyPort());
        }
        return builder.build();
    }
}
