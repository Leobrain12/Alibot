package com.alibot.service;

import com.alibot.domain.Master;
import com.alibot.domain.Role;
import com.alibot.domain.User;
import com.alibot.repository.MasterRepository;
import com.alibot.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Резолвит telegram_user_id в AuthenticatedActor. Используется и ботом (UpdateDispatcher), и
 * Mini App аутентификацией (TelegramInitDataAuthFilter) — единая точка "кто это".
 * ТЗ п.6.1 — если telegram_user_id не входит в разрешённые пользователи, доступа нет
 * (никакого самостоятельного создания аккаунта).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActorResolver {

    private final UserRepository userRepository;
    private final MasterRepository masterRepository;

    public Optional<AuthenticatedActor> resolve(long telegramUserId) {
        Optional<User> userOpt = userRepository.findByTelegramUserId(telegramUserId);
        if (userOpt.isEmpty() || !userOpt.get().isActive()) {
            return Optional.empty();
        }
        User user = userOpt.get();
        UUID masterId = null;
        if (user.getRole() == Role.MASTER) {
            Optional<Master> master = masterRepository.findByUserId(user.getId());
            if (master.isEmpty()) {
                return Optional.empty();
            }
            masterId = master.get().getId();
        }
        return Optional.of(new AuthenticatedActor(user.getId(), user.getTelegramUserId(), user.getRole(), masterId));
    }
}
