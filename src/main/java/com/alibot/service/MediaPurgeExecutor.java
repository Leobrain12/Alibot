package com.alibot.service;

import com.alibot.domain.OrderMedia;
import com.alibot.repository.OrderMediaRepository;
import com.alibot.service.storage.MediaStorage;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Отдельный бин с REQUIRES_NEW — та же причина, что и у IdempotentInsertExecutor: удаление файла
 * с диска необратимо и НЕ участвует в откате БД-транзакции. Если весь пакет обрабатывать в одной
 * транзакции (как было раньше) и на файле N упадёт исключение, файлы 1..N-1 уже физически удалены
 * с диска, но их строки OrderMedia откатятся к purgedAt=null — БД будет утверждать, что файл ещё
 * есть, хотя его больше нет. Здесь же каждая запись — своя физическая транзакция: сбой одной не
 * трогает уже зафиксированные соседние, и не блокирует остаток пакета навсегда.
 */
@Component
@RequiredArgsConstructor
class MediaPurgeExecutor {

    private final OrderMediaRepository mediaRepository;
    private final MediaStorage mediaStorage;
    private final AuditLogService auditLog;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void purgeOne(OrderMedia media) {
        mediaStorage.delete(media.getStoragePath());
        media.setPurgedAt(Instant.now());
        mediaRepository.save(media);
        auditLog.record("MEDIA_PURGED", "ORDER", media.getOrderId(), null, null,
                media.getMediaType() + "/" + media.getId());
    }
}
