package ai.qorva.core.service.orchestrators;

import ai.qorva.core.config.ResumeChatProperties;
import ai.qorva.core.dao.entity.Chat;
import ai.qorva.core.dao.entity.ChatMessage;
import ai.qorva.core.dto.ChatResult;
import ai.qorva.core.dto.ScreeningContext;
import ai.qorva.core.dto.common.TokenUsage;
import ai.qorva.core.enums.ChatUserRole;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.OpenAIService;
import ai.qorva.core.service.UsageMonitoringService;
import ai.qorva.core.utils.TokenEstimator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAgent {

    /** OpenAI error code when the prompt does not fit the model's window; a bug in our budgeting if it ever fires. */
    static final String CONTEXT_LENGTH_EXCEEDED = "context_length_exceeded";

    private final ScreeningContextProvider contextProvider;
    private final OpenAIService openAIService;
    private final UsageMonitoringService usageMonitoringService;
    private final ResumeChatProperties properties;

    /**
     * Answers the latest user message.
     *
     * @param summary      rolling summary of the turns older than {@code unsummarised}, or null
     * @param unsummarised every message after the summary cut-off, oldest first; the window is picked from these
     */
    public ChatMessage answer(Chat chat, String summary, List<ChatMessage> unsummarised) throws QorvaException {
        ScreeningContext ctx = contextProvider.load(chat);
        if (ctx.matchingReportId() != null && !ctx.matchingReportId().equals(chat.getContext().getMatchingReportId())) {
            // A report generated after the chat was created: link it so the header can show it (ChatService saves the chat).
            chat.getContext().setMatchingReportId(ctx.matchingReportId());
            log.info("Resume chat {}: linked screening report {}", chat.getId(), ctx.matchingReportId());
        }

        int contextTokens = TokenEstimator.estimate(ResumeChatPromptBuilder.contextBlock(ctx));
        if (contextTokens > properties.getContextTokenWarn()) {
            log.warn("Resume chat {}: context block is {} estimated tokens (warn threshold {})",
                chat.getId(), contextTokens, properties.getContextTokenWarn());
        }

        int budget = properties.getHistoryTokenBudget();
        ChatResult result;
        ConversationWindowBuilder.Window window;
        long started = System.currentTimeMillis();
        try {
            window = new ConversationWindowBuilder(budget, properties.getKeepRecentMessages()).select(unsummarised);
            result = call(ctx, summary, window, chat);
        } catch (RuntimeException e) {
            if (!isContextLengthExceeded(e)) {
                throw e;
            }
            // Should be unreachable with the budget in place — retry once with half the history rather than fail the turn.
            budget = Math.max(1, budget / 2);
            log.warn("Resume chat {}: model rejected the prompt as too long, retrying with history budget {}", chat.getId(), budget);
            window = new ConversationWindowBuilder(budget, properties.getKeepRecentMessages()).select(unsummarised);
            try {
                result = call(ctx, summary, window, chat);
            } catch (RuntimeException retryFailure) {
                log.error("Resume chat {}: prompt still too long after halving the history budget", chat.getId(), retryFailure);
                throw new QorvaException(QorvaErrorCodes.AI_REQUEST_FAILED, retryFailure);
            }
        }
        long latencyMs = System.currentTimeMillis() - started;

        incrementUsageSilently(chat.getTenantId(), UsageMonitoringService.FeatureKey.AI_RESUME_CHATS);

        log.info("resume-chat turn chatId={} contextTokens={} summaryTokens={} historyTokens={} sentMessages={} droppedMessages={} "
                + "historyBudget={} promptTokens={} completionTokens={} model={} latencyMs={}",
            chat.getId(), contextTokens, TokenEstimator.estimate(summary), window.sentTokens(), window.sent().size(),
            window.dropped().size(), budget, result.promptTokens(), result.completionTokens(), result.model(), latencyMs);

        return ChatMessage.builder()
                .tenantId(chat.getTenantId())
                .chatId(chat.getId())
                .role(ChatUserRole.ASSISTANT)
                .content(result.content())
                .tokens(TokenUsage.builder()
                        .promptTokens(result.promptTokens())
                        .completionTokens(result.completionTokens())
                        .model(result.model())
                        .build())
                .createdAt(Instant.now())
                .build();
    }

    private ChatResult call(ScreeningContext ctx, String summary, ConversationWindowBuilder.Window window, Chat chat) throws QorvaException {
        String language = chat.getMetadata() != null ? chat.getMetadata().getLanguage() : null;
        List<Message> prompt = ResumeChatPromptBuilder.build(ctx, summary, window.sent(), language);
        try {
            return openAIService.chatCompletions(prompt, properties.getModel());
        } catch (QorvaException e) {
            throw e;
        } catch (RuntimeException e) {
            if (isContextLengthExceeded(e)) {
                throw e;
            }
            log.error("Resume chat {}: model call failed", chat.getId(), e);
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

    private void incrementUsageSilently(String tenantId, UsageMonitoringService.FeatureKey key) {
        try {
            usageMonitoringService.incrementUsage(tenantId, key, 1);
        } catch (Exception e) {
            log.warn("Failed to increment {} usage for tenant={}", key, tenantId, e);
        }
    }
}
