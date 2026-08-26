package com.alibot.service;

import com.alibot.config.AppProperties;
import com.alibot.config.CrmConfiguredCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * ТЗ п.85-87 — доставка событий заказа во внешнюю CRM обычным HTTP-вебхуком: POST JSON-снимка
 * на app.crm.webhook-url, с общим секретом в заголовке (проверяется на стороне CRM, у нас нет
 * готовой конкретной системы вроде Bitrix, поэтому это универсальный вебхук-контракт, под
 * который подставляется реальный адаптер конкретной CRM без изменений в CrmSyncService/Scheduler).
 * Активна только когда webhook-url задан — иначе бин NoopCrmSyncGateway.
 */
@Component
@Conditional(CrmConfiguredCondition.class)
public class HttpCrmSyncGateway implements CrmSyncGateway {

    private final RestTemplate crmRestTemplate;
    private final AppProperties.Crm crmProperties;

    public HttpCrmSyncGateway(RestTemplate crmRestTemplate, AppProperties appProperties) {
        this.crmRestTemplate = crmRestTemplate;
        this.crmProperties = appProperties.getCrm();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void send(String eventType, String payloadJson) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Event-Type", eventType);
        if (crmProperties.getWebhookSecret() != null && !crmProperties.getWebhookSecret().isBlank()) {
            headers.set("X-Crm-Secret", crmProperties.getWebhookSecret());
        }
        // 4xx/5xx намеренно выбрасывают исключение (RestTemplate по умолчанию) — CrmSyncScheduler
        // ловит его и планирует повтор, успех определяется только 2xx-ответом.
        crmRestTemplate.postForEntity(crmProperties.getWebhookUrl(), new HttpEntity<>(payloadJson, headers), Void.class);
    }
}
