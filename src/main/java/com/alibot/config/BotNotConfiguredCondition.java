package com.alibot.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Инверсия BotConfiguredCondition — используется NoopNotificationGateway, чтобы ровно один
 *  бин NotificationGateway существовал в контексте в любой конфигурации. */
public class BotNotConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return !new BotConfiguredCondition().matches(context, metadata);
    }
}
