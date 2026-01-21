package ai.qorva.core.dto.common;

import java.util.List;

public record IndustryPreferences(
        List<String> preferredIndustries,
        String strictness
) {}
