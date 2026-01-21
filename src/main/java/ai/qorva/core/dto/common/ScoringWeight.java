package ai.qorva.core.dto.common;

import java.math.BigDecimal;

public record ScoringWeight(
        BigDecimal skills,
        BigDecimal experience,
        BigDecimal location,
        BigDecimal industry
) {}
