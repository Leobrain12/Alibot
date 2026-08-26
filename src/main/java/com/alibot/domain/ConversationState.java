package com.alibot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * ТЗ п.110/111 — server-side FSM для многошаговых сценариев бота (создание заявки, отчёт,
 * перенос, отказ, загрузка детали). Прогресс переживает рестарт процесса, т.к. хранится в БД,
 * а не только в памяти. Черновик, брошенный дольше таймаута, не удаляется, а помечается истёкшим
 * (рекомендация ТЗ п.111) и подчищается плановой задачей.
 */
@Entity
@Table(name = "conversation_states")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationState {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    /** Например CREATE_ORDER, WORK_REPORT, RESCHEDULE, DECLINE_REASON, WAITING_PART. */
    @Column(nullable = false, length = 40)
    private String scenario;

    @Column(nullable = false, length = 40)
    private String step;

    /** JSON-снимок собранных на текущий момент данных сценария. Без @Lob: на PostgreSQL это
     *  включило бы large-object (oid) семантику вместо простого text, что не нужно для
     *  небольшого JSON-черновика — обычный String отлично ложится на text-колонку и в H2, и в PG. */
    @Column(name = "draft_json")
    private String draftJson;

    /** Заказ, к которому привязан сценарий (для WorkReport/переноса/отказа) — может отсутствовать
     *  на этапе CreateOrderWizard, пока заказ ещё не создан. */
    @Column(name = "related_order_id")
    private UUID relatedOrderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Builder.Default
    private boolean expired = false;

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
