package ai.qorva.core.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record InsightRequestDTO(
        @NotBlank String question,
        @Nullable String conversationId,
        @Nullable @Valid List<MentionDTO> mentions
) {
    public List<MentionDTO> mentionsOrEmpty() {
        return mentions == null ? List.of() : mentions;
    }
}
