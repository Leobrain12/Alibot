package com.alibot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * ТЗ п.12 — заказ (уже квалифицированная задача на ремонт, в отличие от маркетингового Lead — ТЗ п.10).
 * Без реальной CRM-интеграции в этом заходе отдельной сущности Lead нет: leadId/crmId/source —
 * это просто nullable-поля для будущей трассируемости (ТЗ п.11).
 * Значение финансовых полей — строго по ТЗ п.13.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    /** ТЗ п.112 — конкурентные изменения (например, старый мастер жмёт "Принять" уже после
     *  того, как админ переназначил заказ) должны быть отклонены с понятной ошибкой, а не
     *  тихо перезаписывать друг друга. Hibernate инкрементирует это поле на каждый UPDATE и
     *  бросает OptimisticLockingFailureException, если версия в БД разошлась с ожидаемой —
     *  перехватывается централизованно в UpdateDispatcher/GlobalExceptionHandler. */
    @Version
    @Column(nullable = false)
    private Long version;

    /** Человекочитаемый номер заказа. Присваивается в OrderService из БД-последовательности
     *  order_number_seq (см. OrderRepository#nextOrderNumber) — не через @GeneratedValue,
     *  так как JPA официально поддерживает генерацию значений только для полей @Id. */
    @Column(nullable = false, unique = true)
    private Long number;

    @Column(name = "lead_id")
    private String leadId;

    @Column(name = "crm_id")
    private String crmId;

    private String source;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_phone", nullable = false)
    private String customerPhone;

    @Column(name = "appliance_type", nullable = false)
    private String applianceType;

    private String brand;

    private String model;

    @Column(nullable = false)
    private String symptom;

    private String description;

    @Column(nullable = false)
    private String address;

    @Column(name = "address_lat")
    private Double addressLat;

    @Column(name = "address_lon")
    private Double addressLon;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "time_from", nullable = false)
    private LocalTime timeFrom;

    @Column(name = "time_to", nullable = false)
    private LocalTime timeTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "master_id")
    private Master master;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private OrderStatus status = OrderStatus.NEW;

    @Column(name = "estimated_price", precision = 12, scale = 2)
    private BigDecimal estimatedPrice;

    @Column(name = "final_price", precision = 12, scale = 2)
    private BigDecimal finalPrice;

    @Column(name = "labor_price", precision = 12, scale = 2)
    private BigDecimal laborPrice;

    @Column(name = "parts_sell_price", precision = 12, scale = 2)
    private BigDecimal partsSellPrice;

    @Column(name = "parts_cost", precision = 12, scale = 2)
    private BigDecimal partsCost;

    @Column(name = "master_payout", precision = 12, scale = 2)
    private BigDecimal masterPayout;

    @Column(name = "amount_paid", precision = 12, scale = 2)
    private BigDecimal amountPaid;

    @Column(name = "admin_comment")
    private String adminComment;

    @Column(name = "master_comment")
    private String masterComment;

    /** Заполняется при статусе CUSTOMER_CANCELLED/MASTER_DECLINED/CANCELLED/RESCHEDULED (ТЗ п.22/31/35). */
    @Column(name = "cancel_reason")
    private String cancelReason;

    /** ТЗ п.64 — гарантийный заказ ссылается на исходный. */
    @Column(name = "warranty_parent_order_id")
    private UUID warrantyParentOrderId;

    /** ТЗ п.32.1 — поля запрошенной детали при статусе WAITING_PART. */
    @Column(name = "part_name")
    private String partName;

    @Column(name = "part_number")
    private String partNumber;

    @Column(name = "part_estimated_cost", precision = 12, scale = 2)
    private BigDecimal partEstimatedCost;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "on_the_way_at")
    private Instant onTheWayAt;

    @Column(name = "arrived_at")
    private Instant arrivedAt;

    /** ТЗ п.83 — напоминание мастеру за N минут до визита, чтобы не слать повторно. */
    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    /** ТЗ п.84 — "Мастер не подтвердил заявку за X минут", чтобы не слать повторно. */
    @Column(name = "accept_timeout_notified_at")
    private Instant acceptTimeoutNotifiedAt;

    @jakarta.persistence.PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @jakarta.persistence.PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** ТЗ п.62 — сколько клиент ещё должен. */
    public BigDecimal amountDue() {
        if (finalPrice == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal paid = amountPaid == null ? BigDecimal.ZERO : amountPaid;
        BigDecimal due = finalPrice.subtract(paid);
        return due.signum() > 0 ? due : BigDecimal.ZERO;
    }
}
