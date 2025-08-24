package ai.qorva.core.dto.common;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenUsage {
    private Long promptTokens;
    private Long completionTokens;
    private String model; // e.g., "gpt-4o-mini"
}