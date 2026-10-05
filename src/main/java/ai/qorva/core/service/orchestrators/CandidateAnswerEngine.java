package ai.qorva.core.service.orchestrators;

import ai.qorva.core.config.ResumeChatProperties;
import ai.qorva.core.dto.ChatResult;
import ai.qorva.core.dto.ScreeningContext;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.OpenAIService;
import ai.qorva.core.service.UsageMonitoringService;
import ai.qorva.core.service.ai.AiCallFailedException;
import ai.qorva.core.service.ai.AiCallMetrics;
import ai.qorva.core.utils.TokenEstimator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Answers a recruiter's question about one candidate for one job — Copilot's {@code ask_about_candidate}.
 * One model call with the whole CV, the job with its scoring rules and the screening report as context,
 * plus a token-bounded window of the conversation's earlier turns. Counts one AI resume chat message.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateAnswerEngine {

    /** OpenAI error code when the prompt does not fit the model's window; a bug in our budgeting if it ever fires. */
    static final String CONTEXT_LENGTH_EXCEEDED = "context_length_exceeded";

    private final ScreeningContextProvider contextProvider;
    private final OpenAIService openAIService;
    private final UsageMonitoringService usageMonitoringService;
    private final ResumeChatProperties properties;

    /** The answer, and the report it was based on (null when the pair has none yet). */
    public record Answer(String text, String matchingReportId, Double finalScore) {}

    /**
     * @param language     language name to answer in (e.g. "French"), or null to follow the question
     * @param earlierTurns the conversation before this question, oldest first; the window is picked from these
     */
    public Answer answer(String tenantId, String cvId, String jobPostId, String language,
                         List<ConversationTurn> earlierTurns, String question) throws QorvaException {
        ScreeningContext ctx = contextProvider.load(tenantId, cvId, jobPostId);

        int contextTokens = TokenEstimator.estimate(ResumeChatPromptBuilder.contextBlock(ctx));
        if (contextTokens > properties.getContextTokenWarn()) {
            log.warn("Candidate answer cv={} job={}: context block is {} estimated tokens (warn threshold {})",
                cvId, jobPostId, contextTokens, properties.getContextTokenWarn());
        }

        int budget = properties.getHistoryTokenBudget();
        ChatResult result;
        ConversationWindowBuilder.Window window;
        long started = System.currentTimeMillis();
        try {
            window = new ConversationWindowBuilder(budget, properties.getKeepRecentMessages()).select(earlierTurns);
            result = call(ctx, window, question, language);
        } catch (RuntimeException e) {
            if (!isContextLengthExceeded(e)) {
                throw e;
            }
            // Should be unreachable with the budget in place — retry once with half the history rather than fail the answer.
            budget = Math.max(1, budget / 2);
            log.warn("Candidate answer cv={} job={}: model rejected the prompt as too long, retrying with history budget {}",
                cvId, jobPostId, budget);
            window = new ConversationWindowBuilder(budget, properties.getKeepRecentMessages()).select(earlierTurns);
            try {
                result = call(ctx, window, question, language);
            } catch (RuntimeException retryFailure) {
                log.error("Candidate answer cv={} job={}: prompt still too long after halving the history budget",
                    cvId, jobPostId, retryFailure);
                throw new QorvaException(QorvaErrorCodes.AI_REQUEST_FAILED, retryFailure);
            }
        }
        long latencyMs = System.currentTimeMillis() - started;

        incrementUsageSilently(tenantId);

        log.info("candidate-answer cv={} job={} contextTokens={} historyTokens={} sentTurns={} droppedTurns={} "
                + "historyBudget={} promptTokens={} completionTokens={} model={} latencyMs={}",
            cvId, jobPostId, contextTokens, window.sentTokens(), window.sent().size(), window.dropped().size(), budget,
            result.promptTokens(), result.completionTokens(), result.model(), latencyMs);

        return new Answer(result.content(), ctx.matchingReportId(), ctx.finalScore());
    }

    private ChatResult call(ScreeningContext ctx, ConversationWindowBuilder.Window window, String question, String language)
        throws QorvaException {
        try {
            return openAIService.chatCompletions(ResumeChatPromptBuilder.build(ctx, window.sent(), question, language),
                properties.getModel());
        } catch (QorvaException e) {
            throw e;
        } catch (RuntimeException e) {
            if (isContextLengthExceeded(e)) {
                throw e;
            }
            if (e instanceof AiCallFailedException failed && AiCallMetrics.TIMEOUT.equals(failed.getOutcome())) {
                // A long answer (a plan, a scorecard) the model could not finish in time: say so, it is not an outage.
                log.warn("Candidate answer: model call timed out", e);
                throw new QorvaException(QorvaErrorCodes.AI_ANSWER_TOO_LONG, e);
            }
            log.error("Candidate answer: model call failed", e);
            throw new QorvaException(QorvaErrorCodes.AI_REQUEST_FAILED, e);
        }
    }

    static boolean isContextLengthExceeded(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(CONTEXT_LENGTH_EXCEEDED)) {
                return true;
            }
        }
        return false;
    }

    private void incrementUsageSilently(String tenantId) {
        try {
            usageMonitoringService.incrementUsage(tenantId, UsageMonitoringService.FeatureKey.AI_RESUME_CHATS, 1);
        } catch (Exception e) {
            log.warn("Failed to increment AI_RESUME_CHATS usage for tenant={}", tenantId, e);
        }
    }
}
