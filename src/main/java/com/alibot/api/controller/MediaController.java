package com.alibot.api.controller;

import com.alibot.api.security.CurrentActor;
import com.alibot.domain.OrderMedia;
import com.alibot.repository.OrderMediaRepository;
import com.alibot.service.MediaService;
import com.alibot.service.exception.NotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Отдаёт содержимое приватного медиафайла — доступ только через это API (не публичный URL),
 *  с той же проверкой прав, что и остальной OrderService (владение заказом). */
@RestController
@RequiredArgsConstructor
public class MediaController {

    private final OrderMediaRepository mediaRepository;
    private final MediaService mediaService;
    private final com.alibot.service.OrderService orderService;
    private final CurrentActor currentActor;

    @GetMapping("/api/v1/media/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable UUID id) {
        OrderMedia media = mediaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Медиафайл не найден"));
        orderService.getById(media.getOrderId(), currentActor.get()); // проверка прав на заказ
        byte[] content = mediaService.readContent(media);
        MediaType type = media.getMimeType() != null ? MediaType.parseMediaType(media.getMimeType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().contentType(type).body(content);
    }
}
