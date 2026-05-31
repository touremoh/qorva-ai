package ai.qorva.core.service;

import ai.qorva.core.dao.entity.InsightConversationTurn;
import ai.qorva.core.dao.repository.InsightConversationTurnRepository;
import ai.qorva.core.dto.InsightConversationSummaryDTO;
import ai.qorva.core.dto.InsightConversationTurnDTO;
import ai.qorva.core.dto.InsightIntent;
import ai.qorva.core.dto.InsightResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsightConversationService {

    private final InsightConversationTurnRepository repository;

    public void saveTurn(String conversationId, String tenantId, String initiatedBy, String title, String question, InsightIntent intent, InsightResponseDTO response) {
        try {
            InsightConversationTurn turn = new InsightConversationTurn();
            turn.setConversationId(conversationId);
            turn.setTenantId(tenantId);
            turn.setInitiatedBy(initiatedBy);
            turn.setTitle(title);
            turn.setQuestion(question);
            turn.setIntent(intent);
            turn.setResponse(response);
            repository.save(turn);
        } catch (Exception e) {
            log.error("Failed to persist conversation turn for conversationId={}: {}", conversationId, e.getMessage());
        }
    }

    public List<InsightConversationTurnDTO> getHistory(String conversationId, String tenantId, String initiatedBy) {
        return repository.findByConversationIdAndTenantIdAndInitiatedByOrderByCreatedAtAsc(conversationId, tenantId, initiatedBy)
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    public List<InsightConversationSummaryDTO> getAllConversations(String tenantId, String initiatedBy) {
        List<InsightConversationTurn> allTurns =
            repository.findByTenantIdAndInitiatedByOrderByCreatedAtAsc(tenantId, initiatedBy);

        // Group chronologically-ordered turns by conversationId (LinkedHashMap preserves insertion order)
        // then sort conversations by their most recent turn descending (newest conversation first)
        return allTurns.stream()
            .collect(Collectors.groupingBy(
                InsightConversationTurn::getConversationId,
                LinkedHashMap::new,
                Collectors.toList()
            ))
            .entrySet().stream()
            .map(entry -> {
                List<InsightConversationTurn> raw = entry.getValue();
                List<InsightConversationTurnDTO> turns = raw.stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
                // Title is stored on the first turn of each conversation
                String title = raw.stream()
                    .map(InsightConversationTurn::getTitle)
                    .filter(t -> t != null && !t.isBlank())
                    .findFirst()
                    .orElse(null);
                Instant lastActivityAt = raw.stream()
                    .map(InsightConversationTurn::getCreatedAt)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
                return new InsightConversationSummaryDTO(entry.getKey(), title, turns, lastActivityAt);
            })
            .sorted(Comparator.comparing(InsightConversationSummaryDTO::lastActivityAt,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .collect(Collectors.toList());
    }

    private InsightConversationTurnDTO toDTO(InsightConversationTurn t) {
        InsightResponseDTO r = t.getResponse();
        return new InsightConversationTurnDTO(
            t.getId(),
            t.getConversationId(),
            t.getInitiatedBy(),
            t.getQuestion(),
            t.getIntent(),
            r != null ? r.answerText() : null,
            r != null ? r.candidates() : List.of(),
            r != null ? r.totalCandidateCount() : 0L,
            r != null ? r.metrics() : List.of(),
            r != null ? r.charts() : List.of(),
            r != null ? r.followUpQuestions() : List.of(),
            r != null ? r.disclaimer() : null,
            t.getCreatedAt()
        );
    }
}
