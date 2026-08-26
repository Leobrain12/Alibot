package com.alibot.api.dto;

import com.alibot.domain.User;
import java.util.UUID;

/** ТЗ п.5.1/9. Как и остальные *Response — явная проекция вместо отдачи JPA-сущности напрямую
 *  (UserController раньше возвращал User целиком, включая поля, которые не должны попадать
 *  в API-ответ без явного решения, и создавая иную форму ответа, чем всё остальное API). */
public record UserResponse(UUID id, Long telegramUserId, String role, String name, String phone, boolean active) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getTelegramUserId(), u.getRole().name(), u.getName(), u.getPhone(), u.isActive());
    }
}
