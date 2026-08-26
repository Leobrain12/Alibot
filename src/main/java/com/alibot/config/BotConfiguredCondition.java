package com.alibot.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Истинно, только если задан непустой bot.token — используется, чтобы весь Telegram-слой
 *  (клиент, бот, уведомления) не поднимался вовсе, когда токена нет (dev без бота). */
public class BotConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String token = context.getEnvironment().getProperty("bot.token", "");
        return token != null && !token.isBlank();
    }
}
