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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateAnswerEngineTest {

	@Mock private ScreeningContextProvider contextProvider;
	@Mock private OpenAIService openAIService;
	@Mock private UsageMonitoringService usageMonitoringService;
	@Spy private ResumeChatProperties properties = new ResumeChatProperties();
	@InjectMocks private CandidateAnswerEngine engine;

	@Test
	void answersFromTheWholeContextAndCountsOneCandidateQuestion() throws QorvaException {
		when(contextProvider.load("t1", "cv", "job")).thenReturn(new ScreeningContext("{cv}", "{job}", "{report}", "r1", 64.0, false));
		when(openAIService.chatCompletions(anyList(), anyString())).thenReturn(new ChatResult("Fit: the report scores 64%", 10L, 5L, "m"));

		var answer = engine.answer("t1", "cv", "job", "French",
			List.of(ConversationTurn.recruiter("Who is she?"), ConversationTurn.assistant("A Java developer.")), "Is she a fit?");

		assertThat(answer.text()).isEqualTo("Fit: the report scores 64%");
		assertThat(answer.matchingReportId()).isEqualTo("r1");
		assertThat(answer.finalScore()).isEqualTo(64.0);
		verify(usageMonitoringService).incrementUsage("t1", UsageMonitoringService.FeatureKey.AI_RESUME_CHATS, 1);

		var model = properties.getModel();
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Message>> prompt = ArgumentCaptor.forClass(List.class);
		verify(openAIService).chatCompletions(prompt.capture(), eq(model));
		var messages = prompt.getValue();
		assertThat(messages.get(0).getText()).contains("Answer in this language: French.");
		assertThat(messages.get(1).getText()).contains("{cv}").contains("{job}").contains("{report}").contains("64%");
		assertThat(messages.get(2).getText()).isEqualTo("Who is she?");
		assertThat(messages.get(3).getText()).isEqualTo("A Java developer.");
		assertThat(messages.getLast()).isInstanceOf(UserMessage.class);
		assertThat(messages.getLast().getText()).isEqualTo("Is she a fit?");
	}

	@Test
	void saysNoReportExistsWhenThePairHasNone() throws QorvaException {
		when(contextProvider.load("t1", "cv", "job")).thenReturn(new ScreeningContext("{cv}", "{job}", null));
		when(openAIService.chatCompletions(anyList(), anyString())).thenReturn(new ChatResult("No report yet", 1L, 1L, "m"));

		var answer = engine.answer("t1", "cv", "job", null, List.of(), "Is she a fit?");

		assertThat(answer.matchingReportId()).isNull();
		assertThat(answer.finalScore()).isNull();
	}

	@Test
	void aTimedOutAnswerSaysItTookTooLong() throws QorvaException {
		when(contextProvider.load("t1", "cv", "job")).thenReturn(new ScreeningContext("{cv}", "{job}", null));
		when(openAIService.chatCompletions(anyList(), anyString()))
			.thenThrow(new AiCallFailedException("resume_chat", AiCallMetrics.TIMEOUT, new RuntimeException("Read timed out")));

		assertThatThrownBy(() -> engine.answer("t1", "cv", "job", null, List.of(), "Prepare a technical interview"))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.AI_ANSWER_TOO_LONG);
		verify(usageMonitoringService, never()).incrementUsage(any(), any(), anyInt());
	}
}
