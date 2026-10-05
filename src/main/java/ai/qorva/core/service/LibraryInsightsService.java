package ai.qorva.core.service;

import ai.qorva.core.dto.*;
import ai.qorva.core.service.orchestrators.FollowUpResolver;
import ai.qorva.core.service.orchestrators.InsightAnswerGenerator;
import ai.qorva.core.service.orchestrators.InsightEntityExtractor;
import ai.qorva.core.service.orchestrators.InsightIntentClassifier;
import ai.qorva.core.service.orchestrators.MentionResolver;
import ai.qorva.core.service.orchestrators.QuestionTranslatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Talent Intelligence: answers a question about the resume library as a whole (counts, distributions,
 * clusters, skill gaps, comparisons) with deterministic handlers, then words the answer. Called by
 * Copilot's {@code analyze_library}; the conversation state between questions is the {@link ConversationFrame},
 * which the caller keeps and passes back with the next question.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LibraryInsightsService {

	private final InsightIntentClassifier intentClassifier;
	private final InsightEntityExtractor entityExtractor;
	private final InsightRouter insightRouter;
	private final InsightAnswerGenerator answerGenerator;
	private final UsageMonitoringService usageMonitoringService;
	private final QuestionTranslatorService questionTranslator;
	private final MentionResolver mentionResolver;
	private final FollowUpResolver followUpResolver;

	/**
	 * The answer, and the frame to pass with the next question of the conversation. {@code clarification} is true
	 * when the question was too broad and the answer asks for specifics; it counts no query.
	 */
	public record Analysis(InsightResponseDTO response, ConversationFrame frame, boolean clarification) {}

	/**
	 * @param previousFrame the frame of the conversation's previous analysis, or null for a question that stands alone
	 */
	public Analysis analyse(String question, List<MentionDTO> mentions, String tenantId, ConversationFrame previousFrame) {
		ObjectId tenantObjectId = new ObjectId(tenantId);

		// Translate to English for classification and extraction; original kept for answer generation
		String englishQuestion = questionTranslator.toEnglish(question);

		log.info("Translated question to English: {}. Original: {}", englishQuestion, question);

		// Classification and extraction are single-shot, so an elliptical follow-up ("java development")
		// has to be made self-contained first — from the previous turn's frame alone, never the transcript.
		FollowUpResolver.Resolution resolution = followUpResolver.resolve(englishQuestion, previousFrame);
		String resolvedQuestion = resolution.question();

		InsightIntent intent = intentClassifier.classify(resolvedQuestion);
		CVQueryParams params = resolveParams(resolvedQuestion, intent, resolution);

		if (params.needsClarification()) {
			// Translate clarification back only when the original question wasn't English
			boolean needsTranslation = !englishQuestion.trim().equalsIgnoreCase(question.trim());
			String clarificationText = needsTranslation
				? questionTranslator.matchLanguageOf(params.clarificationQuestion(), question)
				: params.clarificationQuestion();
			InsightResponseDTO clarification = new InsightResponseDTO(
				null, intent, clarificationText,
				List.of(), 0, List.of(), List.of(), List.of(), null, null
			);
			// Flagged as awaiting clarification so the next utterance is read as the answer to it.
			return new Analysis(clarification, new ConversationFrame(resolvedQuestion, intent, params, true, Instant.now()), true);
		}

		MentionResolver.ResolvedMentions resolvedMentions = mentionResolver.resolve(mentions, tenantId);
		InsightHandlerResult result = insightRouter.route(intent).handle(params, tenantObjectId, resolvedMentions);
		AnswerGenerationResult answer = answerGenerator.generate(result, intent, question, resolvedQuestion, resolvedMentions);

		usageMonitoringService.incrementUsage(tenantId, UsageMonitoringService.FeatureKey.TALENT_INTELLIGENCE_QUERIES, 1);

		InsightResponseDTO response = new InsightResponseDTO(
			null,
			intent,
			answer.answerText(),
			result.candidates(),
			result.totalCount(),
			result.metrics(),
			result.charts(),
			answer.followUpQuestions() != null ? answer.followUpQuestions() : List.of(),
			answer.disclaimer(),
			result.rawData().isEmpty() ? null : result.rawData()
		);
		return new Analysis(response, new ConversationFrame(resolvedQuestion, intent, params, false, Instant.now()), false);
	}

	/**
	 * Extracts filters from the resolved question, then lets the previous turn's filters fill the
	 * slots this turn left empty — that is what keeps "top 10" alive across "show me the top 10
	 * profiles" → "java development". A general recruiting question carries nothing over: it runs no
	 * query, and inheriting filters into it would only leak the previous topic into the next answer.
	 */
	private CVQueryParams resolveParams(String resolvedQuestion, InsightIntent intent, FollowUpResolver.Resolution resolution) {
		CVQueryParams extracted = entityExtractor.extract(resolvedQuestion, intent);

		if (intent == InsightIntent.GENERAL_RECRUITING_QUESTION || !resolution.continuation()) {
			return extracted;
		}

		CVQueryParams merged = extracted.mergeOnto(resolution.carriedParams());

		// A follow-up that inherited concrete filters is no longer too broad to answer, even when
		// the extractor judged the fragment alone to be.
		return merged.needsClarification() && merged.hasAnyFilter()
			? merged.withoutClarification()
			: merged;
	}
}
