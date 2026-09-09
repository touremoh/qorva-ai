package ai.qorva.core.dto;

import java.time.Instant;

/**
 * The bounded slice of conversation state carried from one insight turn to the next:
 * the previous question, the intent it resolved to and the filters that were extracted
 * from it. Fixed schema, so its serialized size stays constant however long the
 * conversation runs — unlike a transcript, which is why only this is replayed.
 *
 * @param awaitingClarification true when the previous turn ended by asking the user for
 *                              more specifics, which makes the current utterance an answer
 *                              to that question rather than a new one.
 */
public record ConversationFrame(
        String englishQuestion,
        InsightIntent intent,
        CVQueryParams params,
        boolean awaitingClarification,
        Instant createdAt
) {}
