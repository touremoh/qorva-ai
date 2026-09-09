package ai.qorva.core.dto;

/**
 * Output of the follow-up resolver: whether the current utterance continues the previous
 * turn and, when it does, the self-contained rewrite the rest of the pipeline runs on.
 *
 * @param relation          NEW_TOPIC or REFINEMENT
 * @param rewrittenQuestion the utterance restated so it stands on its own
 */
public record FollowUpResolution(String relation, String rewrittenQuestion, String reason) {

    public static final String NEW_TOPIC = "NEW_TOPIC";
    public static final String REFINEMENT = "REFINEMENT";

    public boolean isRefinement() {
        return REFINEMENT.equalsIgnoreCase(relation);
    }
}
