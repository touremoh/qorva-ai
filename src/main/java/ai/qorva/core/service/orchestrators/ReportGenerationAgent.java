package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dto.MatchingReportResponse;
import ai.qorva.core.dto.QorvaPromptContextHolder;
import ai.qorva.core.dto.common.MatchingReportDetails;
import ai.qorva.core.dto.common.ScoringRules;
import ai.qorva.core.mapper.OpenAIResultMapper;
import ai.qorva.core.utils.QorvaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import static org.springframework.ai.openai.api.OpenAiApi.ChatModel.GPT_5_MINI;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationAgent {

	private final ChatClient chatClient;
	private final QorvaPromptContextHolder promptContextHolder;
	private final OpenAIResultMapper mapper;

	public MatchingReportDetails generate(String cvDetails, String jobDescription, String languageCode, ScoringRules scoringRules) {
		var outputConverter = new BeanOutputConverter<>(MatchingReportResponse.class);
		var reportGenerationPrompt = promptContextHolder.getReportGenerationPrompt();
		var reportOutputFormat = promptContextHolder.getReportOutputFormat();
		var scoringRulesJson = scoringRules != null ? QorvaUtils.toJSON(scoringRules) : "";

		var apiResponse = chatClient.prompt()
			.options(OpenAiChatOptions.builder()
				.model(GPT_5_MINI)
				.responseFormat(ResponseFormat.builder()
					.type(ResponseFormat.Type.JSON_SCHEMA)
					.jsonSchema(ResponseFormat.JsonSchema.builder()
						.name("report_generation")
						.schema(outputConverter.getJsonSchema())
						.strict(Boolean.TRUE)
						.build())
					.build())
				.temperature(1.0)
				.build())
			.user(u -> u
				.text(reportGenerationPrompt)
				.param("cv_data", cvDetails)
				.param("job_description", jobDescription)
				.param("scoring_rules", scoringRulesJson)
				.param("language", StringUtils.hasText(languageCode) ? languageCode : "en")
				.param("output_format", reportOutputFormat))
			.call()
			.content();

		Assert.notNull(apiResponse, "API response cannot be null");
		return mapper.map(outputConverter.convert(apiResponse));
	}
}
