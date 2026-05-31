package ai.qorva.core.dto;

import java.time.Instant;
import java.util.List;

public record InsightConversationTurnDTO(
        String id,
        String conversationId,
        String initiatedBy,
        String question,
        InsightIntent intent,
        String answerText,
        List<CandidateCardDTO> candidates,
        long totalCandidateCount,
        List<InsightMetricDTO> metrics,
        List<ChartDataDTO> charts,
        List<String> followUpQuestions,
        String disclaimer,
        Instant createdAt
) {}
