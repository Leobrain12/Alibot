package com.alibot.service;

import com.alibot.config.AppProperties;
import com.alibot.domain.ConversationState;
import com.alibot.repository.ConversationStateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ТЗ п.110/111 — server-side FSM для многошаговых сценариев бота. Прогресс живёт в БД, а не
 * только в памяти процесса, поэтому переживает рестарт. Заброшенный дольше таймаута сценарий не
 * удаляется, а помечается истёкшим (черновик остаётся доступным для истории/отладки).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ConversationStateService {

    private final ConversationStateRepository repository;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public ConversationState start(Long chatId, Long telegramUserId, String scenario, String firstStep, UUID relatedOrderId) {
        repository.findFirstByChatIdAndExpiredFalseOrderByUpdatedAtDesc(chatId)
                .ifPresent(existing -> {
                    existing.setExpired(true);
                    repository.save(existing);
                });

        ConversationState state = ConversationState.builder()
                .chatId(chatId)
                .telegramUserId(telegramUserId)
                .scenario(scenario)
                .step(firstStep)
                .relatedOrderId(relatedOrderId)
                .draftJson("{}")
                .build();
        return repository.save(state);
    }

    @Transactional(readOnly = true)
    public Optional<ConversationState> findActive(Long chatId) {
        return repository.findFirstByChatIdAndExpiredFalseOrderByUpdatedAtDesc(chatId);
    }

    @SneakyThrows
    public Map<String, String> readDraft(ConversationState state) {
        if (state.getDraftJson() == null || state.getDraftJson().isBlank()) {
            return new HashMap<>();
        }
        return objectMapper.readValue(state.getDraftJson(), new TypeReference<>() {
        });
    }

    @SneakyThrows
    public ConversationState update(ConversationState state, String step, Map<String, String> draft) {
        state.setStep(step);
        state.setDraftJson(objectMapper.writeValueAsString(draft));
        state.touch();
        return repository.save(state);
    }

    public void complete(ConversationState state) {
        state.setExpired(true);
        repository.save(state);
    }

    /** Плановая подчистка: сценарии, брошенные дольше конфигурируемого таймаута, помечаются истёкшими. */
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void expireStale() {
        Instant threshold = Instant.now().minus(appProperties.getConversation().getTimeoutMinutes(), ChronoUnit.MINUTES);
        List<ConversationState> stale = repository.findByExpiredFalseAndUpdatedAtBefore(threshold);
        for (ConversationState state : stale) {
            state.setExpired(true);
        }
        repository.saveAll(stale);
    }
}
