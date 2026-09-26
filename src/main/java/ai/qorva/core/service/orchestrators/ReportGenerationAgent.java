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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;


@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationAgent {

	private final ChatClient chatClient;
	private final QorvaPromptContextHolder promptContextHolder;
	private final OpenAIResultMapper mapper;

	@Value("${qorva.ai.report.model:gpt-5.6-terra}")
	private String model;

	public MatchingReportDetails generate(String cvDetails, String jobDescription, String languageCode, ScoringRules scoringRules) {
		var outputConverter = new BeanOutputConverter<>(MatchingReportResponse.class);
		var reportGenerationPrompt = promptContextHolder.getReportGenerationPrompt();
		var reportOutputFormat = promptContextHolder.getReportOutputFormat();
		var scoringRulesJson = scoringRules != null ? QorvaUtils.toJSON(scoringRules) : "";

		var apiResponse = chatClient.prompt()
			.options(StructuredOutput.options(model, "report_generation", outputConverter.getJsonSchema(), true, 1.0))
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
