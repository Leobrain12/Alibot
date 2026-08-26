package com.alibot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Проверяет, что весь контекст Spring поднимается без токена бота — то есть REST API/Mini App
 * можно проверить локально даже без реального TELEGRAM_BOT_TOKEN (см. BotConfiguredCondition /
 * NoopNotificationGateway).
 */
@SpringBootTest
@ActiveProfiles("test")
class AlibotApplicationTests {

    @Test
    void contextLoads() {
    }
}
