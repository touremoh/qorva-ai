package ai.qorva.core.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;

public record MentionDTO(
        @NotBlank String type,
        @NotBlank String id,
        @Nullable String name
) {
    public static final String TYPE_CANDIDATE = "candidate";
    public static final String TYPE_JOB = "job";
}
