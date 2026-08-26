package com.alibot.config;

import com.alibot.domain.Role;
import com.alibot.domain.User;
import com.alibot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * ТЗ п.6.1 — без начального SUPERADMIN никто не может попасть в систему (самостоятельная
 * регистрация запрещена). Если задан app.bootstrap.superadmin-telegram-id и такого пользователя
 * ещё нет, создаём его при старте — это единственная точка входа "снаружи" системы.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SuperAdminBootstrap implements ApplicationRunner {

    private final AppProperties appProperties;
    private final UserRepository userRepository;

    @Override
    public void run(ApplicationArguments args) {
        String raw = appProperties.getBootstrap().getSuperadminTelegramId();
        if (raw == null || raw.isBlank()) {
            log.info("app.bootstrap.superadmin-telegram-id не задан — пропускаем бутстрап SUPERADMIN");
            return;
        }
        long telegramId = Long.parseLong(raw.trim());
        if (userRepository.existsByTelegramUserId(telegramId)) {
            return;
        }
        User superadmin = User.builder()
                .telegramUserId(telegramId)
                .role(Role.SUPERADMIN)
                .name("Superadmin")
                .active(true)
                .build();
        userRepository.save(superadmin);
        log.info("Создан начальный SUPERADMIN с telegram_user_id={}", telegramId);
    }
}
