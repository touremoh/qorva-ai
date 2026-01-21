package ai.qorva.core.dto.common;

import ai.qorva.core.enums.SeniorityLevelEnum;

public record ExperienceRequirements(
        Integer minYearsOfExperience,
        Integer minRelevantYears,
        String seniorityLevel
) {}
