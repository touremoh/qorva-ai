package ai.qorva.core.service;

import ai.qorva.core.dto.*;
import ai.qorva.core.dto.common.ReportDetails;
import ai.qorva.core.mapper.OpenAIResultMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import static org.springframework.ai.openai.api.OpenAiApi.ChatModel.GPT_4_O_MINI;

@Slf4j
@Service
public class OpenAIService {
	private final QorvaPromptContextHolder qorvaPromptContextHolder;
	private final OpenAIResultMapper mapper;
	private final ChatClient chatClient;

	@Value("${spring.ai.openai.api-key}")
	private String apiKey;

	@Autowired
	public OpenAIService(QorvaPromptContextHolder qorvaPromptContextHolder, OpenAIResultMapper mapper, ChatClient chatClient) {
		this.chatClient = chatClient;
		this.qorvaPromptContextHolder = qorvaPromptContextHolder;
		this.mapper = mapper;
	}

	protected Flux<String> streamCVExtraction(String cvContent) {
		// Create an output converter
		var converter = new BeanOutputConverter<>(CVOutputDTO.class);

		// Get the prompt template
		var promptTemplate = this.qorvaPromptContextHolder.getCvContentExtractionPromptTemplate();

		// Get the output format
		var cvOutputFormat = this.qorvaPromptContextHolder.getCvOutputFormat();

		// Set temperate at 0 to reduce randomness
		double temperature = 0;

		// Stream the CV extraction
		return this.chatClient
			.prompt()
			.options(
				OpenAiChatOptions
					.builder()
					.withModel(GPT_4_O_MINI)
					.withResponseFormat(new ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, converter.getJsonSchema()))
					.withTemperature(temperature)
					.build()
			)
			.user(u -> u
				.text(promptTemplate)
				.param("cv_data", cvContent)
				.param("output_format", cvOutputFormat)
			)
			.stream()
			.content();
	}

	protected ReportDetails generateReport(String cvDetails, String jobDescription, String language) {
		var reportGenerationPrompt = this.qorvaPromptContextHolder.getReportGenerationPrompt();
		var reportOutputFormat = this.qorvaPromptContextHolder.getReportOutputFormat();
		var outputConverter = new BeanOutputConverter<>(CVScreeningReportOutputDTO.class);

		// Lower the temperature to reduce randomness
		double temperature = 0;

		// Call the API
		var apiResponse = this.chatClient.prompt()
			.options(OpenAiChatOptions
				.builder()
				.withModel(GPT_4_O_MINI)
				.withResponseFormat(new ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, outputConverter.getJsonSchema()))
				.withTemperature(temperature)
				.build()
			)
			.user(u -> u
				.text(reportGenerationPrompt)
				.param("cv_data", cvDetails)
				.param("job_description", jobDescription)
				.param("output_format", reportOutputFormat)
				.param("language", language)
			)
			.stream()
			.content();

		// Convert the API Response into String content
		var content = apiResponse.reduce(String::concat).block();

		// Map the string content into CVScreeningReportDTO and render results
		return this.mapper.map(outputConverter.convert(content));
	}
}
