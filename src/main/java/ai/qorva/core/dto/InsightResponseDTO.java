package ai.qorva.core.dto;

import java.util.List;
import java.util.Map;

public record InsightResponseDTO(
        String conversationId,
        InsightIntent intent,
        String answerText,
        List<CandidateCardDTO> candidates,
        long totalCandidateCount,
        List<InsightMetricDTO> metrics,
        List<ChartDataDTO> charts,
        List<String> followUpQuestions,
        String disclaimer,
        Map<String, Object> rawData
) {}
