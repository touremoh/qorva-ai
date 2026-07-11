package ai.qorva.core.service;

import ai.qorva.core.dto.*;
import ai.qorva.core.service.orchestrators.InsightAnswerGenerator;
import ai.qorva.core.service.orchestrators.InsightEntityExtractor;
import ai.qorva.core.service.orchestrators.InsightIntentClassifier;
import ai.qorva.core.service.orchestrators.MentionResolver;
import ai.qorva.core.service.orchestrators.QuestionTranslatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LibraryInsightsService {

	private final InsightIntentClassifier intentClassifier;
	private final InsightEntityExtractor entityExtractor;
	private final InsightRouter insightRouter;
	private final InsightAnswerGenerator answerGenerator;
	private final UsageMonitoringService usageMonitoringService;
	private final InsightConversationService conversationService;
	private final QuestionTranslatorService questionTranslator;
	private final MentionResolver mentionResolver;

	public InsightResponseDTO  ask(InsightRequestDTO request, String tenantId, String userId) {
		String conversationId = request.conversationId() != null
			? request.conversationId()
			: UUID.randomUUID().toString();

		try {
			ObjectId tenantObjectId = new ObjectId(tenantId);

			// Translate to English for classification and extraction; original kept for answer generation
			String englishQuestion = questionTranslator.toEnglish(request.question());

			log.info("Translated question to English: {}. Original: {}", englishQuestion, request.question());

			InsightIntent intent = intentClassifier.classify(englishQuestion);
			CVQueryParams params = entityExtractor.extract(englishQuestion, intent);

			if (params.needsClarification()) {
				// Translate clarification back only when the original question wasn't English
				boolean needsTranslation = !englishQuestion.trim().equalsIgnoreCase(request.question().trim());
				String clarificationText = needsTranslation
					? questionTranslator.matchLanguageOf(params.clarificationQuestion(), request.question())
					: params.clarificationQuestion();
				InsightResponseDTO clarification = new InsightResponseDTO(
					conversationId, intent, clarificationText,
					List.of(), 0, List.of(), List.of(), List.of(), null, null
				);
				conversationService.saveTurn(conversationId, tenantId, userId, null, request.question(), intent, clarification);
				return clarification;
			}

			MentionResolver.ResolvedMentions resolvedMentions = mentionResolver.resolve(request.mentionsOrEmpty(), tenantId);
			InsightHandlerResult result = insightRouter.route(intent).handle(params, tenantObjectId, resolvedMentions);
			AnswerGenerationResult answer = answerGenerator.generate(result, intent, request.question(), resolvedMentions);

			usageMonitoringService.incrementUsage(tenantId, UsageMonitoringService.FeatureKey.TALENT_INTELLIGENCE_QUERIES, 1);

			InsightResponseDTO response = new InsightResponseDTO(
				conversationId,
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

			// Title is only set on the first turn of a new conversation
			String title = request.conversationId() == null ? answer.conversationTitle() : null;
			conversationService.saveTurn(conversationId, tenantId, userId, title, request.question(), intent, response);

			return response;
		} catch (Exception e) {
			log.error("Error processing library insights request for tenant {}: {}", tenantId, e.getMessage(), e);
			return new InsightResponseDTO(
				conversationId,
				InsightIntent.GENERAL_RECRUITING_QUESTION,
				"I was unable to process your request at this time. Please try again.",
				List.of(), 0, List.of(), List.of(), List.of(), null, null
			);
		}
	}
}
