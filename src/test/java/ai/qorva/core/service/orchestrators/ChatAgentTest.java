package ai.qorva.core.service.orchestrators;

import ai.qorva.core.config.ResumeChatProperties;
import ai.qorva.core.dao.entity.Chat;
import ai.qorva.core.dto.ChatResult;
import ai.qorva.core.dto.ScreeningContext;
import ai.qorva.core.dto.common.ChatContext;
import ai.qorva.core.enums.ChatUserRole;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.OpenAIService;
import ai.qorva.core.service.UsageMonitoringService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAgentTest {

	@Mock private ScreeningContextProvider contextProvider;
	@Mock private OpenAIService openAIService;
	@Mock private UsageMonitoringService usageMonitoringService;
	@Spy private ResumeChatProperties properties = new ResumeChatProperties();
	@InjectMocks private ChatAgent agent;

	@Test
	void linksAReportFoundDuringTheTurnToTheChat() throws QorvaException {
		Chat chat = Chat.builder().id("c1").tenantId("t1").context(ChatContext.builder().cvId("cv").jobPostId("job").build()).build();
		when(contextProvider.load(chat)).thenReturn(new ScreeningContext("{cv}", "{job}", "{report}", "r1", 64.0, false));
		when(openAIService.chatCompletions(anyList(), anyString())).thenReturn(new ChatResult("Fit: the report scores 64%", 10L, 5L, "m"));

		var reply = agent.answer(chat, null, List.of());

		assertThat(chat.getContext().getMatchingReportId()).isEqualTo("r1");
		assertThat(reply.getRole()).isEqualTo(ChatUserRole.ASSISTANT);
		assertThat(reply.getTokens().getPromptTokens()).isEqualTo(10L);
	}

	@Test
	void leavesTheChatUntouchedWhenNoReportExists() throws QorvaException {
		Chat chat = Chat.builder().id("c1").tenantId("t1").context(ChatContext.builder().cvId("cv").jobPostId("job").build()).build();
		when(contextProvider.load(chat)).thenReturn(new ScreeningContext("{cv}", "{job}", null));
		when(openAIService.chatCompletions(anyList(), anyString())).thenReturn(new ChatResult("No report yet", 1L, 1L, "m"));

		agent.answer(chat, null, List.of());

		assertThat(chat.getContext().getMatchingReportId()).isNull();
	}
}
