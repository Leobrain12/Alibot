package com.alibot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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
 * ТЗ п.10-11 — маркетинговая заявка: сырой контакт с сайта/CRM/звонка, ещё не квалифицированный
 * в задачу на ремонт. В отличие от Order, у Lead почти ничего не обязательно, кроме контакта —
 * тип техники/адрес/дата визита выясняются уже при конвертации в Order (см. LeadService).
 */
@Entity
@Table(name = "leads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lead {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_phone", nullable = false)
    private String customerPhone;

    @Column(name = "appliance_type")
    private String applianceType;

    /** Свободный текст: что клиент написал/сказал источнику лида. */
    private String comment;

    /** Откуда пришёл лид — "сайт", "avito", "телефон" и т.п., произвольная строка (не enum —
     *  источники добавляются без релиза, как и справочники ТЗ п.132). */
    private String source;

    /** id этого же лида в системе-источнике (CRM/сайт) — для сверки, не используется внутри. */
    @Column(name = "external_id")
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private LeadStatus status = LeadStatus.NEW;

    @Column(name = "converted_order_id")
    private UUID convertedOrderId;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
