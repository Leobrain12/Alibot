package com.alibot.config;

import com.alibot.domain.Role;
import com.alibot.domain.User;
import com.alibot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Внешние системы (сайт/CRM), обращающиеся через Internal API по X-Internal-Api-Key, действуют
 * от имени этого зарезервированного пользователя — все FK (created_by, changed_by_user_id и т.п.)
 * остаются валидными, а не указывают на несуществующего пользователя. telegram_user_id = -1
 * никогда не встретится у настоящего Telegram-пользователя (id всегда положительные).
 */
@Component
@RequiredArgsConstructor
@Order(1)
public class SystemActorBootstrap implements ApplicationRunner {

    public static final long SYSTEM_TELEGRAM_ID = -1L;

    private final UserRepository userRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByTelegramUserId(SYSTEM_TELEGRAM_ID)) {
            return;
        }
        userRepository.save(User.builder()
                .telegramUserId(SYSTEM_TELEGRAM_ID)
                .role(Role.ADMIN)
                .name("Internal API")
                .active(true)
                .build());
    }
}
