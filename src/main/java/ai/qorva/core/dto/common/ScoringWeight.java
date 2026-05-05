package ai.qorva.core.dto.common;

public record ScoringWeight(
        Double skills,
        Double experience,
        Double location,
        Double industry
) {}
