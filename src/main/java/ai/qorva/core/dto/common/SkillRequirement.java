package ai.qorva.core.dto.common;

public record SkillRequirement(
        String name,
        String importance,
        Double weight,
        Integer minYearsOfExperience,
        Boolean exactSkillOnly
) {}
