package com.alibot.service.storage;

import java.util.UUID;

/**
 * Порт хранения приватных медиафайлов (ТЗ п.54/99 — Telegram не рабочее хранилище, только
 * private object storage, без публичных URL). Сегодня единственная реализация — локальная ФС
 * вне webroot; при необходимости подставляется S3-совместимый адаптер без изменений в MediaService.
 */
public interface MediaStorage {

    /** Сохраняет файл, возвращает относительный storage_path (не публичный URL). */
    String save(UUID orderId, String suggestedFileName, byte[] content);

    byte[] read(String storagePath);

    /** ТЗ п.100 — удаление по истечении срока хранения. Не бросает, если файла уже нет —
     *  идемпотентно, чтобы повторный прогон планировщика после сбоя не падал на середине. */
    void delete(String storagePath);
}
