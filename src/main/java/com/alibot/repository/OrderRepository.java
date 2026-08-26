package com.alibot.repository;

import com.alibot.domain.Order;
import com.alibot.domain.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Все методы ниже, возвращающие Order/List&lt;Order&gt;, явно тянут master через
     * LEFT JOIN FETCH. Это не опционально: spring.jpa.open-in-view=false закрывает Hibernate-
     * сессию сразу по выходу из @Transactional сервисного метода, а Order.master помечен
     * FetchType.LAZY (см. Order.java) — DTO-маппинг (OrderResponse.from) и рендер в боте
     * (OrderPresenter/TelegramNotificationGateway) обращаются к order.getMaster() уже ПОСЛЕ
     * закрытия сессии. Без fetch join это падает с LazyInitializationException, но только на
     * заказах, где master реально назначен — поэтому баг не проявлялся на первых тестах.
     */

    @Query("select o from Order o left join fetch o.master where o.id = :id")
    Optional<Order> findByIdWithMaster(@Param("id") UUID id);

    @Query("select o from Order o left join fetch o.master where o.number = :number")
    Optional<Order> findByNumber(@Param("number") Long number);

    @Query("select o from Order o left join fetch o.master where o.status in :statuses order by o.createdAt desc")
    List<Order> findByStatusInOrderByCreatedAtDesc(@Param("statuses") List<OrderStatus> statuses);

    @Query("""
            select o from Order o left join fetch o.master
            where o.master.id = :masterId and o.status in :statuses order by o.visitDate asc
            """)
    List<Order> findByMasterIdAndStatusInOrderByVisitDateAsc(@Param("masterId") UUID masterId, @Param("statuses") List<OrderStatus> statuses);

    @Query("select o from Order o left join fetch o.master where o.master.id = :masterId order by o.createdAt desc")
    List<Order> findByMasterIdOrderByCreatedAtDesc(@Param("masterId") UUID masterId);

    /** ТЗ п.84 — заявки, ожидающие подтверждения мастером дольше accept-timeout, ещё не эскалированные. */
    @Query("select o from Order o left join fetch o.master where o.status = :status and o.acceptTimeoutNotifiedAt is null")
    List<Order> findByStatusAndAcceptTimeoutNotifiedAtIsNull(@Param("status") OrderStatus status);

    /** ТЗ п.83 — заявки с назначенным мастером и предстоящим визитом, по которым ещё не напомнили. */
    @Query("""
            select o from Order o left join fetch o.master
            where o.status in :statuses and o.reminderSentAt is null and o.master is not null
            """)
    List<Order> findByStatusInAndReminderSentAtIsNullAndMasterIsNotNull(@Param("statuses") List<OrderStatus> statuses);

    /** Используется, когда поиск идёт только по мастеру и/или дате визита, без текстового запроса. */
    @Query("select o from Order o left join fetch o.master order by o.createdAt desc")
    List<Order> findAllByOrderByCreatedAtDesc();

    /** ТЗ п.80 — экспорт: заказы за период, создан в диапазоне. */
    @Query("select o from Order o left join fetch o.master where o.createdAt between :from and :to order by o.createdAt desc")
    List<Order> findByCreatedAtBetweenOrderByCreatedAtDesc(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select o from Order o left join fetch o.master
            where lower(o.customerPhone) like lower(concat('%', :query, '%'))
               or lower(o.customerName) like lower(concat('%', :query, '%'))
               or lower(o.address) like lower(concat('%', :query, '%'))
               or str(o.number) like concat('%', :query, '%')
            order by o.createdAt desc
            """)
    List<Order> search(@Param("query") String query);

    @Query(value = "select nextval('order_number_seq')", nativeQuery = true)
    Long nextOrderNumber();

    // --- Статистика (ТЗ п.75) ---

    @Query("select count(o) from Order o where o.master.id = :masterId and o.createdAt between :from and :to")
    long countAssigned(@Param("masterId") UUID masterId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select count(o) from Order o where o.master.id = :masterId and o.acceptedAt is not null
            and o.createdAt between :from and :to
            """)
    long countAccepted(@Param("masterId") UUID masterId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select count(o) from Order o where o.master.id = :masterId and o.status = 'MASTER_DECLINED'
            and o.createdAt between :from and :to
            """)
    long countDeclined(@Param("masterId") UUID masterId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select count(o) from Order o where o.master.id = :masterId and o.status in ('COMPLETED', 'PAID')
            and o.completedAt between :from and :to
            """)
    long countCompleted(@Param("masterId") UUID masterId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select coalesce(sum(o.finalPrice), 0) from Order o where o.master.id = :masterId
            and o.status in ('COMPLETED', 'PAID') and o.completedAt between :from and :to
            """)
    java.math.BigDecimal sumRevenue(@Param("masterId") UUID masterId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select coalesce(sum(o.partsCost), 0) from Order o where o.master.id = :masterId
            and o.status in ('COMPLETED', 'PAID') and o.completedAt between :from and :to
            """)
    java.math.BigDecimal sumPartsCost(@Param("masterId") UUID masterId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select coalesce(sum(o.masterPayout), 0) from Order o where o.master.id = :masterId
            and o.status in ('COMPLETED', 'PAID') and o.completedAt between :from and :to
            """)
    java.math.BigDecimal sumMasterPayout(@Param("masterId") UUID masterId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select count(o) from Order o where o.master.id = :masterId and o.status = 'WARRANTY_RETURN'
            and o.createdAt between :from and :to
            """)
    long countWarranty(@Param("masterId") UUID masterId, @Param("from") Instant from, @Param("to") Instant to);

    /** ТЗ п.65 — warranty_cost: во что бизнесу реально обходится гарантия. Считаем не по
     *  статусу WARRANTY_RETURN (это флаг на ИСХОДНОМ заказе, у него своя цена уже оплачена
     *  клиентом), а по заказам с непустым warrantyParentOrderId — это отдельные, обычно
     *  бесплатные для клиента визиты, созданные через createWarrantyOrder, и вот их
     *  partsCost/masterPayout — реальные издержки, которые никто клиенту не выставит. */
    @Query("""
            select coalesce(sum(o.partsCost), 0) + coalesce(sum(o.masterPayout), 0) from Order o
            where o.master.id = :masterId and o.warrantyParentOrderId is not null and o.createdAt between :from and :to
            """)
    java.math.BigDecimal sumWarrantyCost(@Param("masterId") UUID masterId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select count(o) from Order o where o.status in ('COMPLETED', 'PAID') and o.completedAt between :from and :to")
    long countCompletedAll(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select coalesce(sum(o.finalPrice), 0) from Order o where o.status in ('COMPLETED', 'PAID') and o.completedAt between :from and :to")
    java.math.BigDecimal sumRevenueAll(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select count(o) from Order o where o.createdAt between :from and :to")
    long countCreatedAll(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select count(o) from Order o where o.status = 'WAITING_PART' and o.createdAt between :from and :to")
    long countWaitingPartAll(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select count(o) from Order o where o.status = 'CUSTOMER_CANCELLED' and o.createdAt between :from and :to")
    long countCustomerCancelledAll(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select count(o) from Order o where o.status = 'WARRANTY_RETURN' and o.createdAt between :from and :to")
    long countWarrantyAll(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select coalesce(sum(o.partsCost), 0) + coalesce(sum(o.masterPayout), 0) from Order o
            where o.warrantyParentOrderId is not null and o.createdAt between :from and :to
            """)
    java.math.BigDecimal sumWarrantyCostAll(@Param("from") Instant from, @Param("to") Instant to);
}
