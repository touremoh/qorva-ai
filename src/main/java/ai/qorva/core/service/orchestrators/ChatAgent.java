package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dao.entity.Chat;
import ai.qorva.core.dao.entity.ChatMessage;
import ai.qorva.core.dto.ChatResult;
import ai.qorva.core.dto.ScreeningContext;
import ai.qorva.core.dto.common.TokenUsage;
import ai.qorva.core.enums.ChatUserRole;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.OpenAIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAgent {

    private final ScreeningContextProvider contextProvider;
    private final OpenAIService openAIService;

    public ChatMessage answer(Chat chat, List<ChatMessage> history) throws QorvaException {
        // Fetch domain context
        ScreeningContext ctx = contextProvider.load(
                chat.getContext().getCvId(),
                chat.getContext().getJobPostId(),
                chat.getContext().getMatchingReportId());

        // Compose prompt with system preamble and condensed history
        String prompt = PromptComposer.compose(ctx, history);

        // stick to the configured model
        // TODO : implements a retry mechanism if LLM fails
        // TODO : Surround with try-catch and set the response as failed if LLM fails
        ChatResult result = openAIService.chatCompletions(prompt);

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
}