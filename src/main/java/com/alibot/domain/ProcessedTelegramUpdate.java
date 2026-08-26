package com.alibot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

/**
 * ТЗ п.90 — идемпотентность: Telegram может доставить один и тот же update повторно.
 * update_id — единственный ключ (Telegram гарантирует его возрастание и уникальность в рамках бота).
 * Реализует Persistable с isNew()==true всегда, чтобы Spring Data всегда делал INSERT (persist),
 * а не UPDATE (merge) — иначе повторный update с тем же id тихо перезаписался бы вместо того,
 * чтобы упасть на уникальном ограничении PK (см. IdempotencyService).
 */
@Entity
@Table(name = "processed_telegram_updates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedTelegramUpdate implements Persistable<Long> {

    @Id
    @Column(name = "update_id")
    private Long updateId;

    @Column(name = "processed_at", nullable = false)
    @Builder.Default
    private Instant processedAt = Instant.now();

    @Override
    @Transient
    public Long getId() {
        return updateId;
    }

    @Override
    @Transient
    public boolean isNew() {
        return true;
    }
}
