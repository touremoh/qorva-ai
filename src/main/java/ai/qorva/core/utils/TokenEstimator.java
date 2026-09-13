package ai.qorva.core.utils;

import lombok.experimental.UtilityClass;

/**
 * Cheap token estimate (~4 chars per token for OpenAI tokenizers on Latin-script text).
 * Only used to size prompt windows and summary triggers — real usage comes back from the
 * model response and is what gets persisted on the message.
 */
@UtilityClass
public class TokenEstimator {

    private static final int CHARS_PER_TOKEN = 4;

    public int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (text.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
    }
}
