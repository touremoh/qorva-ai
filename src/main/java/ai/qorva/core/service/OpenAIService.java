package ai.qorva.core.service;

import ai.qorva.core.dto.ChatResult;
import ai.qorva.core.dto.OpenAIChatResponse;
import ai.qorva.core.dto.common.AIAnalysisReportDetails;
import ai.qorva.core.dto.common.ScoringRules;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.OpenAIResultMapper;
import ai.qorva.core.service.orchestrators.CVExtractionAgent;
import ai.qorva.core.service.orchestrators.ReportGenerationAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static org.springframework.ai.openai.api.OpenAiApi.ChatModel.GPT_5_CHAT_LATEST;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAIService {

	private final CVExtractionAgent cvExtractionAgent;
	private final ReportGenerationAgent reportGenerationAgent;
	private final ChatClient chatClient;
	private final OpenAIResultMapper mapper;

	public String streamCVExtraction(String cvContent) {
		return cvExtractionAgent.extract(cvContent);
	}

	public AIAnalysisReportDetails generateReport(String cvDetails, String jobDescription, String languageCode, ScoringRules scoringRules) {
		return reportGenerationAgent.generate(cvDetails, jobDescription, languageCode, scoringRules);
	}

	public ChatResult chatCompletions(String userMessage) throws QorvaException {
		var outputConverter = new BeanOutputConverter<>(OpenAIChatResponse.class);

		var response = chatClient.prompt()
			.options(OpenAiChatOptions.builder()
				.model(GPT_5_CHAT_LATEST)
				.responseFormat(ResponseFormat.builder()
					.type(ResponseFormat.Type.JSON_SCHEMA)
					.jsonSchema(ResponseFormat.JsonSchema.builder()
						.name("chat_completion")
						.schema(outputConverter.getJsonSchema())
						.strict(Boolean.TRUE)
						.build())
					.build())
				.temperature(1.0)
				.build())
			.user(u -> u.text(userMessage))
			.call()
			.content();

		if (Objects.isNull(response)) {
			throw new QorvaException("Something went wrong. Please try again later");
		}

		return mapper.map(outputConverter.convert(response));
	}
}
