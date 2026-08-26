package com.alibot.service;

import com.alibot.domain.CommissionType;
import com.alibot.domain.Master;
import com.alibot.domain.Role;
import com.alibot.domain.User;
import com.alibot.repository.MasterRepository;
import com.alibot.repository.UserRepository;
import com.alibot.service.exception.NotFoundException;
import com.alibot.service.exception.ValidationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ТЗ п.5.1/9 — только SUPERADMIN создаёт пользователей/администраторов/мастеров. */
@Service
@RequiredArgsConstructor
@Transactional
public class UserManagementService {

    private final UserRepository userRepository;
    private final MasterRepository masterRepository;
    private final AccessControlService accessControl;

    public User createUser(long telegramUserId, String name, String phone, Role role, AuthenticatedActor actor) {
        accessControl.assertIsSuperAdmin(actor);
        FieldValidation.requireNonBlank(name, "Имя обязательно");
        if (role == null) {
            throw new ValidationException("Роль обязательна");
        }
        // telegram_user_id всегда положительный у настоящих Telegram-аккаунтов; -1 (=<=0 покрывает
        // и его) зарезервирован за системным актором Internal API (см. SystemActorBootstrap) —
        // без этой проверки SUPERADMIN мог бы случайно создать пользователя с id=-1 и подменить
        // системного актора.
        if (telegramUserId <= 0) {
            throw new ValidationException("Некорректный telegram_user_id");
        }
        if (userRepository.existsByTelegramUserId(telegramUserId)) {
            throw new ValidationException("Пользователь с таким telegram_user_id уже существует");
        }
        User user = User.builder()
                .telegramUserId(telegramUserId)
                .name(name)
                .phone(phone)
                .role(role)
                .active(true)
                .build();
        return userRepository.save(user);
    }

    public Master createMasterProfile(UUID userId, String name, String phone, CommissionType commissionType,
                                       BigDecimal commissionValue, AuthenticatedActor actor) {
        accessControl.assertIsSuperAdmin(actor);
        FieldValidation.requireNonBlank(name, "Имя мастера обязательно");
        if (commissionType == null) {
            throw new ValidationException("Тип выплаты обязателен");
        }
        // MANUAL — сумма вводится на каждом заказе отдельно, значение здесь не нужно.
        // FIXED/PERCENT без значения — реальная и уже случавшаяся в этом проекте ошибка:
        // WorkReportService.resolvePayout() трактует null как 0, мастер получал бы 0 за КАЖДЫЙ
        // заказ молча, без единого предупреждения. PERCENT дополнительно ограничен 100 —
        // это доля от суммы заказа, больше 100% не бывает по определению.
        if (commissionType != CommissionType.MANUAL) {
            if (commissionValue == null) {
                throw new ValidationException("Для типа выплаты %s нужно указать значение".formatted(commissionType));
            }
            FieldValidation.requireNonNegative(commissionValue, "Значение выплаты");
            if (commissionType == CommissionType.PERCENT && commissionValue.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new ValidationException("Процент не может быть больше 100");
            }
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь " + userId + " не найден"));
        if (user.getRole() != Role.MASTER) {
            throw new ValidationException("У пользователя должна быть роль MASTER");
        }
        Master master = Master.builder()
                .user(user)
                .name(name)
                .phone(phone)
                .commissionType(commissionType)
                .commissionValue(commissionValue)
                .active(true)
                .build();
        return masterRepository.save(master);
    }

    /** ТЗ Figma F9 — редактирование данных уже созданного пользователя (имя/телефон). */
    public User updateProfile(UUID userId, String name, String phone, AuthenticatedActor actor) {
        accessControl.assertIsSuperAdmin(actor);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь " + userId + " не найден"));
        if (name != null && !name.isBlank()) {
            user.setName(name);
        }
        if (phone != null) {
            user.setPhone(phone.isBlank() ? null : phone);
        }
        return userRepository.save(user);
    }

    public User setActive(UUID userId, boolean active, AuthenticatedActor actor) {
        accessControl.assertIsSuperAdmin(actor);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь " + userId + " не найден"));
        user.setActive(active);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<User> list(AuthenticatedActor actor) {
        accessControl.assertIsSuperAdmin(actor);
        return userRepository.findAll();
    }
}
