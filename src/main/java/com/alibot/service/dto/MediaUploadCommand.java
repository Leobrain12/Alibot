package com.alibot.service.dto;

import com.alibot.domain.MediaStage;
import com.alibot.domain.MediaType;

/** Метаданные одного файла, уже полученного из Telegram (getFile выполнен на уровне bot-слоя —
 *  MediaService лишь сохраняет результат и применяет доменные правила: лимиты размера/количества). */
public record MediaUploadCommand(
        MediaType mediaType,
        MediaStage stage,
        String telegramFileId,
        String telegramFileUniqueId,
        String mimeType,
        String originalFileName,
        long fileSize,
        Integer durationSeconds,
        Integer width,
        Integer height,
        byte[] content
) {
}
