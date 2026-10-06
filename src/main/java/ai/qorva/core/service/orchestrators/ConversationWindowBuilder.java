package ai.qorva.core.service.orchestrators;

import ai.qorva.core.utils.TokenEstimator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Picks the earlier turns that are sent verbatim to the model: walks backwards from the
 * newest turn until the token budget is spent, but never fewer than {@code keepRecent}
 * turns so an answer always has its immediate context. Pure — no I/O — so it is unit-tested
 * in isolation.
 */
public class ConversationWindowBuilder {

    private final int historyTokenBudget;
    private final int keepRecent;

    public ConversationWindowBuilder(int historyTokenBudget, int keepRecent) {
        this.historyTokenBudget = historyTokenBudget;
        this.keepRecent = keepRecent;
    }

    /** Result of a window selection: the turns to send (oldest first) and the ones left out. */
    public record Window(List<ConversationTurn> sent, List<ConversationTurn> dropped, int sentTokens) {}

    /**
     * @param turns earlier turns of the conversation, oldest first; blank ones are ignored
     */
    public Window select(List<ConversationTurn> turns) {
        List<ConversationTurn> eligible = turns.stream()
            .filter(t -> t.text() != null && !t.text().isBlank())
            .toList();

        List<ConversationTurn> sent = new ArrayList<>();
        int tokens = 0;
        for (int i = eligible.size() - 1; i >= 0; i--) {
            ConversationTurn t = eligible.get(i);
            int cost = TokenEstimator.estimate(t.text());
            boolean mustKeep = sent.size() < keepRecent;
            if (!mustKeep && tokens + cost > historyTokenBudget) {
                break;
            }
            sent.add(t);
            tokens += cost;
        }
        Collections.reverse(sent);

        List<ConversationTurn> dropped = eligible.subList(0, eligible.size() - sent.size());
        return new Window(sent, List.copyOf(dropped), tokens);
    }
}
