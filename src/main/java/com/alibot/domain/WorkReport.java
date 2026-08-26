package com.alibot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/** ТЗ п.39-46/56-58 — обязательный отчёт мастера, без которого заказ нельзя перевести в COMPLETED. */
@Entity
@Table(name = "work_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkReport {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "visit_id")
    private UUID visitId;

    @Column(name = "master_id", nullable = false)
    private UUID masterId;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "work_description", nullable = false)
    private String workDescription;

    @Column(name = "labor_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal laborPrice;

    @Column(name = "parts_sell_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal partsSellPrice;

    @Column(name = "parts_cost", precision = 12, scale = 2)
    private BigDecimal partsCost;

    @Column(name = "final_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalPrice;

    @Column(name = "master_payout", precision = 12, scale = 2)
    private BigDecimal masterPayout;

    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "confirmed_at")
    private Instant confirmedAt;
}
