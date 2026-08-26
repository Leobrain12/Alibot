package com.alibot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * ТЗ п.90 — проверяем эмпирически (не по памяти о поведении Hibernate/Spring), что повторная
 * доставка одного и того же update_id действительно тихо игнорируется, а не роняет
 * UnexpectedRollbackException — именно это и есть весь смысл класса.
 */
@SpringBootTest
@ActiveProfiles("test")
class IdempotencyServiceTest {

    @Autowired
    private IdempotencyService idempotencyService;

    @Test
    void secondCallWithSameUpdateIdReturnsFalseWithoutThrowing() {
        long updateId = 999_001L;

        assertThat(idempotencyService.markIfFirstTime(updateId)).isTrue();

        assertThatCode(() -> {
            boolean second = idempotencyService.markIfFirstTime(updateId);
            assertThat(second).isFalse();
        }).doesNotThrowAnyException();
    }
}
