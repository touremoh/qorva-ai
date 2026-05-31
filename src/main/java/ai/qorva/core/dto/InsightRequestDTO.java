package ai.qorva.core.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;

public record InsightRequestDTO(
        @NotBlank String question,
        @Nullable String conversationId
) {}
