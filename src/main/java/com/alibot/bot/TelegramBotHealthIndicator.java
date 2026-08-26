package com.alibot.bot;

import com.alibot.config.BotConfiguredCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/** ТЗ п.104 — "webhook uptime" / состояние бота, доступно через GET /actuator/health. */
@Component("telegramBot")
@Conditional(BotConfiguredCondition.class)
@RequiredArgsConstructor
public class TelegramBotHealthIndicator implements HealthIndicator {

    private final BotBootstrap botBootstrap;

    @Override
    public Health health() {
        return botBootstrap.isHealthy()
                ? Health.up().build()
                : Health.down().withDetail("reason", "long polling сессия не активна").build();
    }
}
