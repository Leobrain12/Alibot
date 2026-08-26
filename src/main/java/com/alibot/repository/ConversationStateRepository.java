package com.alibot.repository;

import com.alibot.domain.ConversationState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationStateRepository extends JpaRepository<ConversationState, UUID> {
    Optional<ConversationState> findFirstByChatIdAndExpiredFalseOrderByUpdatedAtDesc(Long chatId);
    List<ConversationState> findByExpiredFalseAndUpdatedAtBefore(Instant threshold);
}
