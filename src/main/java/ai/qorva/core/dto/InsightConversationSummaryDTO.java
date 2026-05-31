package ai.qorva.core.dto;

import java.time.Instant;
import java.util.List;

public record InsightConversationSummaryDTO(
        String conversationId,
        String title,
        List<InsightConversationTurnDTO> turns,
        Instant lastActivityAt
) {}
