package com.alibot.service;

import com.alibot.config.AppProperties;
import com.alibot.domain.MediaType;
import com.alibot.domain.Order;
import com.alibot.domain.OrderMedia;
import com.alibot.repository.OrderMediaRepository;
import com.alibot.service.dto.MediaUploadCommand;
import com.alibot.service.exception.NotFoundException;
import com.alibot.service.exception.ValidationException;
import com.alibot.service.storage.MediaStorage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ТЗ п.48-55 — фото/видео заказа. Скачивание файла из Telegram (getFile/downloadFile) остаётся
 * в bot-слое (это транспортная деталь Telegram); сюда приходят уже готовые байты + метаданные,
 * а доменные правила (лимиты размера/количества, куда и как сохранить) живут здесь.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MediaService {

    private final OrderMediaRepository mediaRepository;
    private final MediaStorage mediaStorage;
    private final AppProperties appProperties;
    private final AccessControlService accessControl;
    private final OrderService orderService;
    private final AuditLogService auditLog;
    private final MediaPurgeExecutor purgeExecutor;

    public OrderMedia upload(UUID orderId, MediaUploadCommand cmd, AuthenticatedActor actor) {
        Order order = orderService.findOrThrow(orderId);
        accessControl.assertIsAssignedMaster(actor, order);

        long maxBytes = (cmd.mediaType() == MediaType.VIDEO
                ? appProperties.getMedia().getMaxVideoSizeMb()
                : appProperties.getMedia().getMaxPhotoSizeMb()) * 1024L * 1024L;
        if (cmd.fileSize() > maxBytes) {
            throw new ValidationException(
                    "Файл слишком большой. Максимальный размер для загрузки в систему — %d МБ."
                            .formatted(maxBytes / (1024 * 1024)));
        }

        long existingCount = mediaRepository.findByOrderIdOrderByCreatedAtAsc(orderId).size();
        if (existingCount >= appProperties.getMedia().getMaxMediaCountPerVisit()) {
            throw new ValidationException("Достигнут лимит медиафайлов по заказу ("
                    + appProperties.getMedia().getMaxMediaCountPerVisit() + ")");
        }

        String storagePath = mediaStorage.save(orderId, cmd.originalFileName(), cmd.content());

        OrderMedia media = OrderMedia.builder()
                .orderId(orderId)
                .uploadedByUserId(actor.userId())
                .mediaType(cmd.mediaType())
                .stage(cmd.stage())
                .telegramFileId(cmd.telegramFileId())
                .telegramFileUniqueId(cmd.telegramFileUniqueId())
                .mimeType(cmd.mimeType())
                .originalFileName(cmd.originalFileName())
                .fileSize(cmd.fileSize())
                .durationSeconds(cmd.durationSeconds())
                .width(cmd.width())
                .height(cmd.height())
                .storagePath(storagePath)
                .build();
        media = mediaRepository.save(media);
        auditLog.record("MEDIA_ADDED", "ORDER", orderId, actor.userId(), null,
                cmd.mediaType() + "/" + cmd.stage());
        return media;
    }

    @Transactional(readOnly = true)
    public List<OrderMedia> list(UUID orderId, AuthenticatedActor actor) {
        Order order = orderService.getById(orderId, actor);
        return mediaRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());
    }

    @Transactional(readOnly = true)
    public byte[] readContent(OrderMedia media) {
        if (media.getPurgedAt() != null) {
            throw new NotFoundException("Медиафайл удалён по истечении срока хранения");
        }
        return mediaStorage.read(media.getStoragePath());
    }

    /** ТЗ п.100 — вызывается планировщиком (MediaRetentionScheduler), только когда
     *  app.media.retention-days > 0. Удаляет файл с диска, но не саму запись OrderMedia —
     *  история заказа ("медиа было приложено") не должна исчезать вместе с содержимым.
     *  Не @Transactional: каждая запись обрабатывается в своей физической транзакции через
     *  MediaPurgeExecutor (см. его комментарий) — иначе сбой на файле N откатывает в БД уже
     *  физически удалённые файлы 1..N-1 к "как будто не удалены". Сбой одной записи логируется
     *  и не останавливает обработку остальных в этом же прогоне. */
    @Transactional(readOnly = true)
    public void purgeExpired() {
        int retentionDays = appProperties.getMedia().getRetentionDays();
        if (retentionDays <= 0) {
            return;
        }
        Instant threshold = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        List<OrderMedia> expired = mediaRepository.findByPurgedAtIsNullAndCreatedAtBefore(threshold);
        int purged = 0;
        for (OrderMedia media : expired) {
            try {
                purgeExecutor.purgeOne(media);
                purged++;
            } catch (Exception e) {
                log.error("Не удалось удалить по сроку хранения медиафайл {} (заказ {})",
                        media.getId(), media.getOrderId(), e);
            }
        }
        if (purged > 0) {
            log.info("Удалено по истечении срока хранения ({} дней): {} медиафайлов", retentionDays, purged);
        }
    }

    @Transactional(readOnly = true)
    public long countPhotos(UUID orderId) {
        return mediaRepository.countByOrderIdAndMediaType(orderId, MediaType.PHOTO);
    }

    @Transactional(readOnly = true)
    public long countVideos(UUID orderId) {
        return mediaRepository.countByOrderIdAndMediaType(orderId, MediaType.VIDEO);
    }
}
