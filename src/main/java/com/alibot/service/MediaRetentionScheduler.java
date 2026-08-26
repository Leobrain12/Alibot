package com.alibot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** ТЗ п.100 — фоновая очистка медиафайлов по истечении срока хранения. Сам MediaService
 *  проверяет app.media.retention-days > 0 и ничего не делает, если удаление не включено. */
@Component
@RequiredArgsConstructor
public class MediaRetentionScheduler {

    private final MediaService mediaService;

    @Scheduled(cron = "${app.media.retention-cron}")
    public void purgeExpiredMedia() {
        mediaService.purgeExpired();
    }
}
