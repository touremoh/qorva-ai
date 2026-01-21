package ai.qorva.core.dto.common;

public record ClientReportMetrics(
        Integer totalAnalyzed,
        Integer shortlistedCount,
        Integer topFitScore,
        Double averageFitScore
) {}