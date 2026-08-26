package com.alibot.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Истинно, только если задан непустой app.crm.webhook-url — по умолчанию CRM не настроена. */
public class CrmConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String url = context.getEnvironment().getProperty("app.crm.webhook-url", "");
        return url != null && !url.isBlank();
    }
}
