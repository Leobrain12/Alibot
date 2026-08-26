package com.alibot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/** ТЗ п.49 — универсальная сущность для всех фото/видео заказа (вместо отдельной WorkReportPhoto). */
@Entity
@Table(name = "order_media")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderMedia {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "visit_id")
    private UUID visitId;

    @Column(name = "work_report_id")
    private UUID workReportId;

    @Column(name = "uploaded_by_user_id", nullable = false)
    private UUID uploadedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 10)
    private MediaType mediaType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaStage stage;

    @Column(name = "telegram_file_id", nullable = false)
    private String telegramFileId;

    @Column(name = "telegram_file_unique_id")
    private String telegramFileUniqueId;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "original_file_name")
    private String originalFileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    private Integer width;

    private Integer height;

    /** Путь в приватном хранилище (см. service.storage.MediaStorage) — не публичный URL. */
    @Column(name = "storage_path")
    private String storagePath;

    private String caption;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /** ТЗ п.100 — срок хранения. Запись переживает удаление файла: purgedAt != null значит
     *  содержимое стёрто с диска, но факт "медиа было" остаётся в истории заказа. */
    @Column(name = "purged_at")
    private Instant purgedAt;
}
