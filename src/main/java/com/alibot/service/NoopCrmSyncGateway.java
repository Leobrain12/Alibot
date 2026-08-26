package com.alibot.service;

import com.alibot.config.CrmNotConfiguredCondition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/** Гарантирует, что бин CrmSyncGateway существует всегда, даже без настроенной CRM
 *  (app.crm.webhook-url пуст по умолчанию) — CrmSyncService на него жёстко завязан. */
@Component
@Conditional(CrmNotConfiguredCondition.class)
@Slf4j
public class NoopCrmSyncGateway implements CrmSyncGateway {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void send(String eventType, String payloadJson) {
        log.debug("[noop-crm] {} (CRM не настроена, app.crm.webhook-url пуст)", eventType);
    }
}
