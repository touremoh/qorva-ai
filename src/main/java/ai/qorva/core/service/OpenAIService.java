package ai.qorva.core.service;

import ai.qorva.core.service.ai.AiCallMetrics;
import org.springframework.beans.factory.annotation.Qualifier;

import ai.qorva.core.dto.ChatResult;
import ai.qorva.core.dto.common.MatchingReportDetails;
import ai.qorva.core.dto.common.ScoringRules;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.orchestrators.CVExtractionAgent;
import ai.qorva.core.service.orchestrators.CVVisionExtractionAgent;
import ai.qorva.core.service.orchestrators.ReportGenerationAgent;
import ai.qorva.core.utils.CVPageImageRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAIService {

	private final CVExtractionAgent cvExtractionAgent;
	private final CVVisionExtractionAgent cvVisionExtractionAgent;
	private final ReportGenerationAgent reportGenerationAgent;
	@Qualifier("copilotAnswerChatClient") private final ChatClient chatClient;

	public String streamCVExtraction(String cvContent) {
		return cvExtractionAgent.extract(cvContent);
	}

	public String streamCVVisionExtraction(String partialText, java.util.List<CVPageImageRenderer.PageImage> pages) {
		return cvVisionExtractionAgent.extract(partialText, pages);
	}

	/** See {@link ReportGenerationAgent#version()}. */
	public String reportVersion() {
		return reportGenerationAgent.version();
	}

	public MatchingReportDetails generateReport(String cvDetails, String jobDescription, String languageCode, ScoringRules scoringRules) {
		return reportGenerationAgent.generate(cvDetails, jobDescription, languageCode, scoringRules);
	}

	/**
	 * One candidate answer (Copilot's ask_about_candidate). Returns the answer text plus the usage the
	 * provider reported, which the engine logs so prompt growth stays visible.
	 */
	public ChatResult chatCompletions(List<Message> messages, String model) throws QorvaException {
		var response = chatClient.prompt()
			.advisors(a -> a.param(AiCallMetrics.AGENT, "resume_chat"))
			.options(OpenAiChatOptions.builder()
				.model(model)
				// GPT-5.x only accepts the default temperature (1). Leaving it unset is not enough: Spring AI merges
				// its own default (0.7) into the request, which the model rejects with 400 unsupported_value.
				.temperature(1.0)
				.build())
			.messages(messages)
			.call()
			.chatResponse();

		if (Objects.isNull(response) || Objects.isNull(response.getResult())
			|| Objects.isNull(response.getResult().getOutput())
			|| !StringUtils.hasText(response.getResult().getOutput().getText())) {
			throw new QorvaException(QorvaErrorCodes.AI_REQUEST_FAILED);
		}

		var usage = response.getMetadata() != null ? response.getMetadata().getUsage() : null;
		var reportedModel = response.getMetadata() != null && StringUtils.hasText(response.getMetadata().getModel())
			? response.getMetadata().getModel() : model;
		return new ChatResult(
			response.getResult().getOutput().getText(),
			usage == null || usage.getPromptTokens() == null ? 0L : usage.getPromptTokens().longValue(),
			usage == null || usage.getCompletionTokens() == null ? 0L : usage.getCompletionTokens().longValue(),
			reportedModel);
	}
}
