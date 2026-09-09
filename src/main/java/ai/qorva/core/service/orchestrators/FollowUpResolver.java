package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dto.CVQueryParams;
import ai.qorva.core.dto.ConversationFrame;
import ai.qorva.core.dto.FollowUpResolution;
import ai.qorva.core.dto.QorvaPromptContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.ai.openai.api.OpenAiApi.ChatModel.GPT_4_1_MINI;

/**
 * Turns a follow-up utterance ("java development") into a self-contained question
 * ("show me the top 10 profiles: java development") before it reaches the intent classifier
 * and the entity extractor, which are both single-shot and would otherwise see the fragment alone.
 *
 * <p>Only the single previous {@link ConversationFrame} is replayed, never the transcript, so the
 * prompt this adds is the same size on turn 2 and on turn 20. The clarification-answer case —
 * the assistant asked for specifics and the user supplied them — is resolved deterministically
 * and costs no model call at all.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpResolver {

	private final ChatClient chatClient;
	private final QorvaPromptContextHolder promptContextHolder;
	private final ObjectMapper objectMapper;

	/** A frame older than this is treated as a finished topic: the user has moved on. */
	@Value("${qorva.ai.insights.follow-up.frame-ttl-minutes:30}")
	private long frameTtlMinutes;

	/** Keeps the replayed question bounded, so one rambling turn cannot inflate every later prompt. */
	@Value("${qorva.ai.insights.follow-up.max-previous-question-length:300}")
	private int maxPreviousQuestionLength;

	/**
	 * @param question       the question the rest of the pipeline should run on
	 * @param carriedParams  filters from the previous turn to merge under the freshly extracted
	 *                       ones, or null when this utterance starts a new topic
	 */
	public record Resolution(String question, CVQueryParams carriedParams, boolean continuation) {

		static Resolution standalone(String question) {
			return new Resolution(question, null, false);
		}
	}

	public Resolution resolve(String englishQuestion, ConversationFrame previousFrame) {
		if (englishQuestion == null || englishQuestion.isBlank() || previousFrame == null) {
			return Resolution.standalone(englishQuestion);
		}

		if (isExpired(previousFrame)) {
			log.debug("Previous insight frame older than {} minutes; treating question as a new topic", frameTtlMinutes);
			return Resolution.standalone(englishQuestion);
		}

		// The previous turn ended by asking the user for specifics, so this utterance answers it
		// by construction — no need to spend a model call deciding that.
		if (previousFrame.awaitingClarification()) {
			String spliced = splice(previousFrame.englishQuestion(), englishQuestion);
			log.info("Pairing answer to clarification; resolved question: {}", spliced);
			return new Resolution(spliced, previousFrame.params(), true);
		}

		FollowUpResolution resolution = classifyFollowUp(englishQuestion, previousFrame);
		if (resolution == null || !resolution.isRefinement()) {
			return Resolution.standalone(englishQuestion);
		}

		String rewritten = resolution.rewrittenQuestion();
		if (rewritten == null || rewritten.isBlank()) {
			rewritten = splice(previousFrame.englishQuestion(), englishQuestion);
		}
		log.info("Resolved follow-up to: {} (reason: {})", rewritten, resolution.reason());
		return new Resolution(rewritten, previousFrame.params(), true);
	}

	private FollowUpResolution classifyFollowUp(String englishQuestion, ConversationFrame previousFrame) {
		var converter = new BeanOutputConverter<>(FollowUpResolution.class);
		var promptTemplate = promptContextHolder.getFollowUpResolverPrompt();

		if (promptTemplate == null || promptTemplate.isBlank()) {
			return null;
		}

		try {
			String renderedPrompt = promptTemplate
				.replace("{{previous_question}}", truncate(previousFrame.englishQuestion()))
				.replace("{{previous_intent}}", previousFrame.intent() != null ? previousFrame.intent().name() : "UNKNOWN")
				.replace("{{previous_filters}}", compactFilters(previousFrame.params()))
				.replace("{{question}}", englishQuestion);

			String content = chatClient.prompt()
				.options(OpenAiChatOptions.builder()
					.model(GPT_4_1_MINI)
					.responseFormat(ResponseFormat.builder()
						.type(ResponseFormat.Type.JSON_SCHEMA)
						.jsonSchema(ResponseFormat.JsonSchema.builder()
							.name("follow_up_resolution")
							.schema(converter.getJsonSchema())
							.strict(Boolean.FALSE)
							.build())
						.build())
					.temperature(0.0)
					.build())
				.messages(new UserMessage(renderedPrompt))
				.call()
				.content();

			return objectMapper.readValue(content, FollowUpResolution.class);
		} catch (Exception e) {
			// Falling back to the raw utterance is the pre-existing behaviour — degraded, never broken.
			log.error("Error resolving follow-up question, treating it as a new topic: {}", e.getMessage());
			return null;
		}
	}

	private boolean isExpired(ConversationFrame frame) {
		if (frame.createdAt() == null) {
			return false;
		}
		return frame.createdAt().isBefore(Instant.now().minus(Duration.ofMinutes(frameTtlMinutes)));
	}

	/** Joins the clarified question to the answer that completes it, e.g. "show me the top 10 profiles: java development". */
	private String splice(String previousQuestion, String utterance) {
		if (previousQuestion == null || previousQuestion.isBlank()) {
			return utterance;
		}
		String stem = truncate(previousQuestion).stripTrailing();
		while (!stem.isEmpty() && (stem.endsWith("?") || stem.endsWith(".") || stem.endsWith(":"))) {
			stem = stem.substring(0, stem.length() - 1).stripTrailing();
		}
		return stem.isEmpty() ? utterance : stem + ": " + utterance.trim();
	}

	private String truncate(String value) {
		if (value == null) {
			return "";
		}
		return value.length() <= maxPreviousQuestionLength ? value : value.substring(0, maxPreviousQuestionLength);
	}

	/**
	 * Serializes only the slots that actually carry a value. Keeps the replayed frame at a few
	 * dozen tokens instead of the 21 mostly-null fields of {@link CVQueryParams}.
	 */
	private String compactFilters(CVQueryParams params) {
		if (params == null) {
			return "{}";
		}
		Map<String, Object> filters = new LinkedHashMap<>();
		putIfPresent(filters, "skills", params.skills());
		putIfPresent(filters, "requiredSkills", params.requiredSkills());
		putIfPresent(filters, "roles", params.roles());
		putIfPresent(filters, "industries", params.industries());
		putIfPresent(filters, "requiredIndustries", params.requiredIndustries());
		putIfPresent(filters, "languages", params.languages());
		putIfPresent(filters, "companies", params.companies());
		putIfPresent(filters, "degreeLevels", params.degreeLevels());
		putIfPresent(filters, "institutions", params.institutions());
		putIfPresent(filters, "tags", params.tags());
		putIfPresent(filters, "applicantNumbers", params.applicantNumbers());
		putIfPresent(filters, "seniority", params.seniority());
		putIfPresent(filters, "skillDepth", params.skillDepth());
		putIfPresent(filters, "leadershipLevel", params.leadershipLevel());
		putIfPresent(filters, "openToWork", params.openToWork());
		putIfPresent(filters, "availabilityStatus", params.availabilityStatus());
		putIfPresent(filters, "location", params.location());
		putIfPresent(filters, "minYearsExperience", params.minYearsExperience());
		putIfPresent(filters, "limit", params.limit());
		putIfPresent(filters, "jobPostReference", params.jobPostReference());

		try {
			return objectMapper.writeValueAsString(filters);
		} catch (Exception e) {
			log.warn("Could not serialize previous filters: {}", e.getMessage());
			return "{}";
		}
	}

	private void putIfPresent(Map<String, Object> target, String key, Object value) {
		if (value == null) {
			return;
		}
		if (value instanceof List<?> list && list.isEmpty()) {
			return;
		}
		target.put(key, value);
	}
}
