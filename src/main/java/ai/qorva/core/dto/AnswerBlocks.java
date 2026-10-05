package ai.qorva.core.dto;

import java.util.List;
import java.util.Map;

/**
 * The rich part of a Copilot answer produced by a library analysis: what Talent Intelligence's handlers
 * computed (candidate cards, metrics, charts, the comparison's raw data), shown under the answer text.
 */
public record AnswerBlocks(
        InsightIntent intent,
        List<CandidateCardDTO> candidates,
        long totalCandidateCount,
        List<InsightMetricDTO> metrics,
        List<ChartDataDTO> charts,
        List<String> followUpQuestions,
        String disclaimer,
        Map<String, Object> rawData
) {
    public static AnswerBlocks of(InsightResponseDTO response) {
        return new AnswerBlocks(response.intent(), response.candidates(), response.totalCandidateCount(), response.metrics(),
            response.charts(), response.followUpQuestions(), response.disclaimer(), response.rawData());
    }
}
