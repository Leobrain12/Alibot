package com.alibot.service;

/**
 * Порт: "нужно доставить событие о заказе во внешнюю CRM". Единственная реализация —
 * HttpCrmSyncGateway (обычный вебхук по HTTP), но сервисный слой (CrmSyncService,
 * CrmSyncScheduler) о деталях транспорта ничего не знает — та же граница, что и у
 * NotificationGateway.
 */
public interface CrmSyncGateway {

    /** Если false — CrmSyncService не ставит события в очередь вовсе (CRM не настроена). */
    boolean isEnabled();

    /**
     * Синхронная попытка доставки. Бросает исключение при сбое (сеть, не-2xx ответ) —
     * CrmSyncScheduler перехватывает и планирует повтор.
     */
    void send(String eventType, String payloadJson);
}
