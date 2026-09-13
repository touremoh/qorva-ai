package ai.qorva.core.dto.common;

import lombok.*;

import java.time.Instant;

/**
 * Rolling, LLM-written summary of the part of a chat that is no longer sent verbatim.
 * Messages created at or before {@code upToMessageCreatedAt} are represented only by
 * {@code text}; everything after it is still eligible for the recent-turns window.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSummary {
    private String text;
    private String upToMessageId;
    private Instant upToMessageCreatedAt;
    private Integer messageCount;
    private Integer tokens;
    private String model;
    private Instant updatedAt;
}
