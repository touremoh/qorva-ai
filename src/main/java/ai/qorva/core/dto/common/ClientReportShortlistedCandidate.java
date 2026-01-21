package ai.qorva.core.dto.common;

import org.springframework.data.mongodb.core.mapping.FieldType;

import java.util.List;

public record ClientReportShortlistedCandidate(
        String candidateId,
        String candidateName,
        Integer fitScore,
        List<String> strengths,
        String experienceSnapshot,
        ClientReportResume resume
) {}