package ai.qorva.core.dto;

import java.util.List;
import java.util.Map;

public record InsightHandlerResult(
        List<CandidateCardDTO> candidates,
        long totalCount,
        List<InsightMetricDTO> metrics,
        List<ChartDataDTO> charts,
        Map<String, Object> rawData
) {
    public static InsightHandlerResult empty() {
        return new InsightHandlerResult(List.of(), 0, List.of(), List.of(), Map.of());
    }
}
