package com.alibot.service.storage;

import com.alibot.config.AppProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * ТЗ п.99/101 — хранит файлы вне public web root, генерирует безопасное имя (не доверяет
 * оригинальному имени файла от клиента), доступ только через MediaStorage#read (никогда напрямую
 * по пути), исключает выполнение загруженного файла (файлы никогда не кладутся в static/ и не
 * обслуживаются как исполняемые ресурсы).
 */
@Component
@RequiredArgsConstructor
public class LocalFileSystemMediaStorage implements MediaStorage {

    private final AppProperties appProperties;

    @Override
    public String save(UUID orderId, String suggestedFileName, byte[] content) {
        try {
            String extension = extractSafeExtension(suggestedFileName);
            String safeName = UUID.randomUUID() + extension;
            Path dir = rootDir().resolve(orderId.toString());
            Files.createDirectories(dir);
            Path target = dir.resolve(safeName);
            Files.write(target, content);
            return orderId + "/" + safeName;
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось сохранить медиафайл", e);
        }
    }

    @Override
    public byte[] read(String storagePath) {
        try {
            Path path = rootDir().resolve(storagePath).normalize();
            if (!path.startsWith(rootDir())) {
                throw new SecurityException("Попытка выйти за пределы каталога хранилища медиа");
            }
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось прочитать медиафайл", e);
        }
    }

    @Override
    public void delete(String storagePath) {
        try {
            Path path = rootDir().resolve(storagePath).normalize();
            if (!path.startsWith(rootDir())) {
                throw new SecurityException("Попытка выйти за пределы каталога хранилища медиа");
            }
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось удалить медиафайл по истечении срока хранения", e);
        }
    }

    private Path rootDir() {
        return Path.of(appProperties.getMedia().getStorageDir()).toAbsolutePath().normalize();
    }

    private String extractSafeExtension(String suggestedFileName) {
        if (suggestedFileName == null) {
            return "";
        }
        int dot = suggestedFileName.lastIndexOf('.');
        if (dot < 0 || dot == suggestedFileName.length() - 1) {
            return "";
        }
        String ext = suggestedFileName.substring(dot).toLowerCase();
        // Разрешаем только известные медиа-расширения — не доверяем произвольному имени клиента.
        return switch (ext) {
            case ".jpg", ".jpeg", ".png", ".webp", ".mp4", ".mov", ".mkv" -> ext;
            default -> "";
        };
    }
}
