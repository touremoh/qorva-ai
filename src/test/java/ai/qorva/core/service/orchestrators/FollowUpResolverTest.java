package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dto.CVQueryParams;
import ai.qorva.core.dto.ConversationFrame;
import ai.qorva.core.dto.InsightIntent;
import ai.qorva.core.dto.QorvaPromptContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FollowUpResolverTest {

	@Mock
	private ChatClient chatClient;

	@Mock
	private QorvaPromptContextHolder promptContextHolder;

	private FollowUpResolver resolver;

	@BeforeEach
	void setUp() {
		resolver = new FollowUpResolver(chatClient, promptContextHolder, new ObjectMapper());
		ReflectionTestUtils.setField(resolver, "frameTtlMinutes", 30L);
		ReflectionTestUtils.setField(resolver, "maxPreviousQuestionLength", 300);
	}

	private static ConversationFrame clarificationFrame(String question, Instant createdAt) {
		var params = new CVQueryParams(
			List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
			null, null, null, null, null, null, null, List.of(), 10,
			List.of(), List.of(), "Which technology?", List.of(), null
		);
		return new ConversationFrame(question, InsightIntent.CANDIDATE_RANKING, params, true, createdAt);
	}

	@Test
	void answerToAClarificationIsPairedWithoutSpendingAModelCall() {
		var frame = clarificationFrame("show me the top 10 profiles", Instant.now());

		var resolution = resolver.resolve("java development", frame);

		assertThat(resolution.question()).isEqualTo("show me the top 10 profiles: java development");
		assertThat(resolution.continuation()).isTrue();
		assertThat(resolution.carriedParams().limit()).isEqualTo(10);
		verifyNoInteractions(chatClient);
	}

	@Test
	void pairingStripsTrailingPunctuationFromTheClarifiedQuestion() {
		var frame = clarificationFrame("who are our best profiles?", Instant.now());

		assertThat(resolver.resolve("java development", frame).question())
			.isEqualTo("who are our best profiles: java development");
	}

	@Test
	void aQuestionWithNoPreviousTurnStandsOnItsOwn() {
		var resolution = resolver.resolve("show me the top 10 java profiles", null);

		assertThat(resolution.question()).isEqualTo("show me the top 10 java profiles");
		assertThat(resolution.continuation()).isFalse();
		assertThat(resolution.carriedParams()).isNull();
		verifyNoInteractions(chatClient);
	}

	@Test
	void aFrameOlderThanTheTtlIsNotCarried() {
		var stale = clarificationFrame("show me the top 10 profiles", Instant.now().minus(2, ChronoUnit.HOURS));

		var resolution = resolver.resolve("java development", stale);

		assertThat(resolution.question()).isEqualTo("java development");
		assertThat(resolution.continuation()).isFalse();
		verifyNoInteractions(chatClient);
	}

	private static ConversationFrame answeredFrame(String question) {
		return new ConversationFrame(question, InsightIntent.CANDIDATE_RANKING,
			CVQueryParams.empty(), false, Instant.now());
	}

	/**
	 * The regression this guards: "how many economics graduates do we have?" asked seconds after
	 * "show me the top candidates in the field of economics" was folded into one rewrite asking
	 * for both, which classified as a profile list and returned nothing. Sharing a subject is not
	 * continuity — a complete question stands on its own.
	 */
	@Test
	void aCompleteQuestionOnTheSameSubjectIsNotAFollowUp() {
		var frame = answeredFrame("show me the top candidates in the field of economics");

		var resolution = resolver.resolve("how many economics graduates do we have?", frame);

		assertThat(resolution.question()).isEqualTo("how many economics graduates do we have?");
		assertThat(resolution.continuation()).isFalse();
		assertThat(resolution.carriedParams()).isNull();
		// The prompt is never even loaded: the decision is made before the model is consulted.
		verifyNoInteractions(promptContextHolder);
		verifyNoInteractions(chatClient);
	}

	@Test
	void aBareFragmentIsStillResolvedAgainstThePreviousTurn() {
		// No request of its own, so it cannot stand alone — this must reach the model.
		resolver.resolve("java development", answeredFrame("show me the top 10 profiles"));

		verify(promptContextHolder).getFollowUpResolverPrompt();
	}

	@Test
	void aQuestionPointingBackAtThePreviousAnswerIsNotSelfContained() {
		// "who" looks like a complete request until "among them" is read.
		resolver.resolve("who among them is based in Belgium?", answeredFrame("show me java profiles"));

		verify(promptContextHolder).getFollowUpResolverPrompt();
	}

	@Test
	void aDeltaOnThePreviousRequestIsNotSelfContained() {
		// "show me 20 instead" reads as a complete request but only adjusts the previous one.
		resolver.resolve("show me 20 instead", answeredFrame("show me the top 10 java profiles"));

		verify(promptContextHolder).getFollowUpResolverPrompt();
	}

	@Test
	void aMissingResolverPromptDegradesToTheRawUtterance() {
		var answered = new ConversationFrame(
			"show me the top 10 java profiles", InsightIntent.CANDIDATE_RANKING,
			CVQueryParams.empty(), false, Instant.now()
		);

		var resolution = resolver.resolve("only in Belgium", answered);

		assertThat(resolution.question()).isEqualTo("only in Belgium");
		assertThat(resolution.continuation()).isFalse();
	}
}
