package ai.qorva.core.dto.common;

import java.math.BigDecimal;

public record SkillRequirement(
        String name,
        String importance,
        BigDecimal weight,
        Integer minYearsOfExperience,
        Boolean exactSkillOnly
) {}
