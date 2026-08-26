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
 * ТЗ п.95 — общий журнал действий (отдельно от OrderStatusHistory, которая фиксирует только
 * смену статуса заказа). oldValue/newValue — компактные текстовые снимки, не полный object diff:
 * этого достаточно для "кто/что/когда сделал", не превращая лог в дублирующее хранилище доменных
 * данных.
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    /** ТЗ п.95.1: ORDER_CREATED, MASTER_ASSIGNED, MASTER_CHANGED, STATUS_CHANGED, PRICE_CHANGED,
     *  PAYMENT_CHANGED, WORK_REPORT_CREATED, MEDIA_ADDED, ORDER_CANCELLED, WARRANTY_CREATED. */
    @Column(nullable = false, length = 40)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
