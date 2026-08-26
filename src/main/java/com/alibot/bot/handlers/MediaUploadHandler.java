package com.alibot.bot.handlers;

import com.alibot.bot.BotSender;
import com.alibot.config.BotConfiguredCondition;
import com.alibot.domain.MediaStage;
import com.alibot.domain.MediaType;
import com.alibot.domain.OrderMedia;
import com.alibot.service.AuthenticatedActor;
import com.alibot.service.MediaService;
import com.alibot.service.dto.MediaUploadCommand;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.Video;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * ТЗ п.47/52/53 — фото/видео сохраняются на КАЖДОЕ сообщение с медиа, а не только на первое
 * сообщение альбома: Telegram доставляет каждый файл media group отдельным Update с тем же
 * media_group_id, и мы обрабатываем каждый Update независимо (без буферизации), что уже
 * гарантирует нужное поведение без дополнительной сложности.
 * ТЗ п.55 — лимит скачивания файла ботом через обычный Bot API составляет 20 МБ, проверяем
 * заявленный размер до попытки скачивания.
 */
@Component
@Conditional(BotConfiguredCondition.class)
@RequiredArgsConstructor
@Slf4j
public class MediaUploadHandler {

    private static final long MAX_TELEGRAM_BOT_API_DOWNLOAD_BYTES = 20L * 1024 * 1024;

    private final TelegramClient telegramClient;
    private final MediaService mediaService;
    private final BotSender sender;

    public void handle(Message message, MediaStage stage, UUID orderId, AuthenticatedActor actor) {
        long chatId = message.getChatId();
        try {
            if (message.hasPhoto()) {
                List<PhotoSize> sizes = message.getPhoto();
                PhotoSize best = sizes.get(sizes.size() - 1); // Telegram отдаёт по возрастанию размера
                save(orderId, chatId, best.getFileId(), best.getFileUniqueId(), MediaType.PHOTO, stage,
                        "photo.jpg", "image/jpeg", best.getFileSize() == null ? 0 : best.getFileSize(),
                        null, best.getWidth(), best.getHeight(), actor);
            } else if (message.hasVideo()) {
                Video video = message.getVideo();
                save(orderId, chatId, video.getFileId(), video.getFileUniqueId(), MediaType.VIDEO, stage,
                        video.getFileName() != null ? video.getFileName() : "video.mp4",
                        video.getMimeType(), video.getFileSize() == null ? 0 : video.getFileSize(),
                        video.getDuration(), video.getWidth(), video.getHeight(), actor);
            }
        } catch (Exception e) {
            log.warn("Не удалось обработать медиа от {}: {}", chatId, e.getMessage());
            sender.send(chatId, "Не удалось сохранить файл. Попробуйте ещё раз.");
        }
    }

    private void save(UUID orderId, long chatId, String fileId, String fileUniqueId, MediaType type,
                       MediaStage stage, String fileName, String mimeType, long declaredSize, Integer duration,
                       Integer width, Integer height, AuthenticatedActor actor) throws TelegramApiException, IOException {
        if (declaredSize > MAX_TELEGRAM_BOT_API_DOWNLOAD_BYTES) {
            sender.send(chatId, "Видео слишком большое. Максимальный размер для загрузки в систему — 20 МБ. "
                    + "Сожмите видео или отправьте более короткий фрагмент.");
            return;
        }
        File tgFile = telegramClient.execute(GetFile.builder().fileId(fileId).build());
        java.io.File localFile = telegramClient.downloadFile(tgFile);
        byte[] content = Files.readAllBytes(localFile.toPath());
        Files.deleteIfExists(localFile.toPath());

        OrderMedia uploaded = mediaService.upload(orderId, new MediaUploadCommand(
                type, stage, fileId, fileUniqueId, mimeType, fileName, content.length, duration, width, height, content
        ), actor);

        long photos = mediaService.countPhotos(uploaded.getOrderId());
        long videos = mediaService.countVideos(uploaded.getOrderId());
        sender.send(chatId, (type == MediaType.PHOTO ? "Фото" : "Видео")
                + " сохранено.\nФото: " + photos + "\nВидео: " + videos);
    }
}
