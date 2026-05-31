package ai.qorva.core.service;

import ai.qorva.core.dto.*;
import ai.qorva.core.service.orchestrators.InsightAnswerGenerator;
import ai.qorva.core.service.orchestrators.InsightEntityExtractor;
import ai.qorva.core.service.orchestrators.InsightIntentClassifier;
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

	public InsightResponseDTO  ask(InsightRequestDTO request, String tenantId, String userId) {
		String conversationId = request.conversationId() != null
			? request.conversationId()
			: UUID.randomUUID().toString();

		try {
			ObjectId tenantObjectId = new ObjectId(tenantId);

			InsightIntent intent = intentClassifier.classify(request.question());
			ExtractedFilters filters = entityExtractor.extract(request.question(), intent);
			InsightHandlerResult result = insightRouter.route(intent).handle(filters, tenantObjectId);
			AnswerGenerationResult answer = answerGenerator.generate(result, intent, request.question());

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
				answer.disclaimer()
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
				List.of(), 0, List.of(), List.of(), List.of(), null
			);
		}
	}
}
