package com.alibot.repository;

import com.alibot.domain.Master;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MasterRepository extends JpaRepository<Master, UUID> {

    /**
     * Master.user — тот же FetchType.LAZY + spring.jpa.open-in-view=false случай, что и
     * Order.master (см. комментарий в OrderRepository): он не падает сегодня только потому, что
     * все текущие вызовы .getUser() случаются синхронно внутри открытой @Transactional-сессии
     * (например TelegramNotificationGateway, вызываемый из OrderService прямо перед return).
     * Явные fetch-join'ы здесь — не косметика, а профилактика того же LazyInitializationException,
     * который уже один раз ловили в проде на Order.master.
     */
    @Query("select m from Master m left join fetch m.user where m.id = :id")
    Optional<Master> findByIdWithUser(@Param("id") UUID id);

    @Query("select m from Master m left join fetch m.user where m.user.id = :userId")
    Optional<Master> findByUserId(@Param("userId") UUID userId);

    @Query("select m from Master m left join fetch m.user where m.user.telegramUserId = :telegramUserId")
    Optional<Master> findByUser_TelegramUserId(@Param("telegramUserId") Long telegramUserId);
}
