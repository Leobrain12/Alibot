package com.alibot.service;

import com.alibot.domain.ProcessedTelegramUpdate;
import com.alibot.repository.ProcessedTelegramUpdateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Отдельный бин, а не метод внутри IdempotencyService — важно для REQUIRES_NEW: Spring
 * применяет @Transactional через прокси только к вызовам ИЗВНЕ объекта, self-invocation
 * (this.insert(...) из соседнего метода того же класса) прокси обходит и аннотация тихо
 * игнорируется. Здесь же IdempotencyService вызывает этот бин через настоящий прокси, поэтому
 * сбой flush() (нарушение уникальности update_id) помечает rollback-only ТОЛЬКО эту отдельную
 * физическую транзакцию — вызывающий код ловит DataIntegrityViolationException уже вне неё,
 * и его собственная транзакция (если есть) не задета.
 */
@Component
@RequiredArgsConstructor
class IdempotentInsertExecutor {

    private final ProcessedTelegramUpdateRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(long updateId) {
        // saveAndFlush форсирует немедленный INSERT, чтобы конфликт по PK всплыл здесь же.
        repository.saveAndFlush(ProcessedTelegramUpdate.builder().updateId(updateId).build());
    }
}
