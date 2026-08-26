package com.alibot.repository;

import com.alibot.domain.MediaType;
import com.alibot.domain.OrderMedia;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderMediaRepository extends JpaRepository<OrderMedia, UUID> {
    List<OrderMedia> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
    long countByOrderIdAndMediaType(UUID orderId, MediaType mediaType);

    /** ТЗ п.100 — кандидаты на удаление по сроку хранения: ещё не удалённые, старше порога. */
    List<OrderMedia> findByPurgedAtIsNullAndCreatedAtBefore(Instant threshold);
}
