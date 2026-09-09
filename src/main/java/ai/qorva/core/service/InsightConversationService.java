package ai.qorva.core.service;

import ai.qorva.core.dao.entity.InsightConversationTurn;
import ai.qorva.core.dao.repository.InsightConversationTurnRepository;
import ai.qorva.core.dto.ConversationFrame;
import ai.qorva.core.dto.InsightConversationSummaryDTO;
import ai.qorva.core.dto.InsightConversationTurnDTO;
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

    public void saveTurn(String conversationId, String tenantId, String initiatedBy, String title, String question, ConversationFrame frame, InsightResponseDTO response) {
        try {
            InsightConversationTurn turn = new InsightConversationTurn();
            turn.setConversationId(conversationId);
            turn.setTenantId(tenantId);
            turn.setInitiatedBy(initiatedBy);
            turn.setTitle(title);
            turn.setQuestion(question);
            turn.setEnglishQuestion(frame.englishQuestion());
            turn.setIntent(frame.intent());
            turn.setQueryParams(frame.params());
            turn.setAwaitingClarification(frame.awaitingClarification());
            turn.setResponse(response);
            repository.save(turn);
        } catch (Exception e) {
            log.error("Failed to persist conversation turn for conversationId={}: {}", conversationId, e.getMessage());
        }
    }

    /**
     * The state carried into the next question: intent and filters of the most recent turn only.
     * Returns null when there is nothing to carry, which makes the question stand on its own.
     */
    public ConversationFrame findLatestFrame(String conversationId, String tenantId, String initiatedBy) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        try {
            return repository.findFirstByConversationIdAndTenantIdAndInitiatedByOrderByCreatedAtDesc(conversationId, tenantId, initiatedBy)
                .map(turn -> new ConversationFrame(
                    // Turns written before conversation state was persisted only have the raw question.
                    turn.getEnglishQuestion() != null ? turn.getEnglishQuestion() : turn.getQuestion(),
                    turn.getIntent(),
                    turn.getQueryParams(),
                    turn.isAwaitingClarification(),
                    turn.getCreatedAt()
                ))
                .orElse(null);
        } catch (Exception e) {
            log.error("Failed to load previous turn for conversationId={}: {}", conversationId, e.getMessage());
            return null;
        }
    }

    public List<InsightConversationTurnDTO> getHistory(String conversationId, String tenantId, String initiatedBy) {
        return repository.findByConversationIdAndTenantIdAndInitiatedByOrderByCreatedAtAsc(conversationId, tenantId, initiatedBy)
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    public void deleteConversation(String conversationId, String tenantId, String initiatedBy) {
        repository.deleteByConversationIdAndTenantIdAndInitiatedBy(conversationId, tenantId, initiatedBy);
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
            r != null ? r.rawData() : null,
            t.getCreatedAt()
        );
    }
}
