package com.alibot.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Инверсия CrmConfiguredCondition — используется NoopCrmSyncGateway, чтобы ровно один
 *  бин CrmSyncGateway существовал в контексте в любой конфигурации. */
public class CrmNotConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return !new CrmConfiguredCondition().matches(context, metadata);
    }
}
