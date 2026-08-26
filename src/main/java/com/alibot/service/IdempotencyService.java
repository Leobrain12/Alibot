package com.alibot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * ТЗ п.90 — Telegram может доставить один и тот же update повторно; один и тот же update_id
 * не должен дважды изменить статус / создать два отчёта / два заказа / дважды сохранить платёж.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotentInsertExecutor insertExecutor;

    /**
     * true, если update обрабатывается впервые (и тут же помечается обработанным).
     *
     * Намеренно НЕ @Transactional: сама попытка вставки идёт через отдельный бин с
     * REQUIRES_NEW (см. IdempotentInsertExecutor) — конфликт по уникальному update_id должен
     * ломать только ЕГО собственную физическую транзакцию, а не транзакцию этого метода
     * (иначе перехват исключения здесь не спасает — коммит внешней транзакции всё равно
     * упадёт с UnexpectedRollbackException, потому что Hibernate помечает транзакцию
     * rollback-only сразу при сбое flush(), независимо от того, поймали вы исключение или нет).
     */
    public boolean markIfFirstTime(long updateId) {
        try {
            insertExecutor.insert(updateId);
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }
}
