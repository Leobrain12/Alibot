package com.alibot.service;

import com.alibot.domain.Role;
import java.util.UUID;

/**
 * Кто выполняет действие — единственная модель "текущего пользователя", которую видят сервисы.
 * Не имеет значения, пришёл ли вызов из Telegram-бота, из Mini App или из внешнего API:
 * все они резолвят вызывающего в этот же тип на границе транспортного слоя.
 */
public record AuthenticatedActor(UUID userId, Long telegramUserId, Role role, UUID masterId) {

    public boolean isSuperAdmin() {
        return role == Role.SUPERADMIN;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN || role == Role.SUPERADMIN;
    }

    public boolean isMaster() {
        return role == Role.MASTER;
    }

    /** Системный вызов от внешнего API (сайт/CRM) — действует от имени администратора, но без Telegram-контекста. */
    public static AuthenticatedActor system(UUID systemUserId) {
        return new AuthenticatedActor(systemUserId, null, Role.ADMIN, null);
    }
}
