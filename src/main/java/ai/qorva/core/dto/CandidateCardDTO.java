package ai.qorva.core.dto;

import java.util.List;

public record CandidateCardDTO(
        String id,
        String name,
        String currentRole,
        List<String> topSkills,
        String seniorityLevel,
        Double matchScore,
        String locationHint
) {}
