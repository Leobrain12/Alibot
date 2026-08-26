package com.alibot.api.controller;

import com.alibot.api.security.CurrentActor;
import com.alibot.service.AuthenticatedActor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mini App использует это, чтобы узнать роль текущего пользователя и отрисовать нужное меню —
 *  роль всегда резолвится сервером из initData, а не выбирается в интерфейсе (в отличие от
 *  переключателя ADMIN/MASTER в исходном HTML-прототипе, который был лишь демо-заглушкой). */
@RestController
@RequiredArgsConstructor
public class MeController {

    private final CurrentActor currentActor;

    @GetMapping("/api/v1/me")
    public MeResponse me() {
        AuthenticatedActor actor = currentActor.get();
        return new MeResponse(actor.userId(), actor.role().name(), actor.masterId());
    }

    public record MeResponse(java.util.UUID userId, String role, java.util.UUID masterId) {
    }
}
