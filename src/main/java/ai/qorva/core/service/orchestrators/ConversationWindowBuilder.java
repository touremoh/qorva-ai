package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dao.entity.ChatMessage;
import ai.qorva.core.enums.ChatUserRole;
import ai.qorva.core.utils.TokenEstimator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Picks the recent messages that are sent verbatim to the model: walks backwards from the
 * newest message until the token budget is spent, but never fewer than {@code keepRecent}
 * messages so a turn always has its immediate context. Pure — no I/O — so it is unit-tested
 * in isolation. Callers pass messages already filtered to those after the summary cut-off.
 */
public class ConversationWindowBuilder {

    private final int historyTokenBudget;
    private final int keepRecent;

    public ConversationWindowBuilder(int historyTokenBudget, int keepRecent) {
        this.historyTokenBudget = historyTokenBudget;
        this.keepRecent = keepRecent;
    }

    /** Result of a window selection: the messages to send (oldest first) and the ones left out. */
    public record Window(List<ChatMessage> sent, List<ChatMessage> dropped, int sentTokens) {}

    /**
     * @param messages un-summarised messages of the chat, oldest first; SYSTEM rows are ignored
     */
    public Window select(List<ChatMessage> messages) {
        List<ChatMessage> eligible = messages.stream()
            .filter(m -> m.getRole() != ChatUserRole.SYSTEM)
            .toList();

        List<ChatMessage> sent = new ArrayList<>();
        int tokens = 0;
        for (int i = eligible.size() - 1; i >= 0; i--) {
            ChatMessage m = eligible.get(i);
            int cost = TokenEstimator.estimate(m.getContent());
            boolean mustKeep = sent.size() < keepRecent;
            if (!mustKeep && tokens + cost > historyTokenBudget) {
                break;
            }
            sent.add(m);
            tokens += cost;
        }
        Collections.reverse(sent);

        List<ChatMessage> dropped = eligible.subList(0, eligible.size() - sent.size());
        return new Window(sent, List.copyOf(dropped), tokens);
    }

    /** Estimated tokens of every non-system message in the list. */
    public static int estimateTokens(List<ChatMessage> messages) {
        return messages.stream()
            .filter(m -> m.getRole() != ChatUserRole.SYSTEM)
            .mapToInt(m -> TokenEstimator.estimate(m.getContent()))
            .sum();
    }
}
