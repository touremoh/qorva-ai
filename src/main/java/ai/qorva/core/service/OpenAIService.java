package ai.qorva.core.service;

import ai.qorva.core.dto.*;
import ai.qorva.core.dto.common.AIAnalysisReportDetails;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.OpenAIResultMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Objects;

import static org.springframework.ai.openai.api.OpenAiApi.ChatModel.GPT_5_CHAT_LATEST;
import static org.springframework.ai.openai.api.OpenAiApi.ChatModel.GPT_5_MINI;

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

	public String streamCVExtraction(String cvContent) {
		// Create an output converter
		var converter = new BeanOutputConverter<>(CVOutputDTO.class);

		// Get the prompt template
		var promptTemplate = this.qorvaPromptContextHolder.getCvContentExtractionPromptTemplate();

		// Get the output format
		var cvOutputFormat = this.qorvaPromptContextHolder.getCvOutputFormat();

		try {
			// Stream the CV extraction
			return this.chatClient
				.prompt()
				.options(
					OpenAiChatOptions.builder()
						.model(GPT_5_MINI)
						.responseFormat(ResponseFormat
							.builder()
							.type(ResponseFormat.Type.JSON_SCHEMA)
							.jsonSchema(ResponseFormat.JsonSchema
								.builder()
								.name("cv_parser")
								.schema(converter.getJsonSchema())
								.strict(Boolean.TRUE)
								.build())
							.build()
						)
						.temperature(1.0)
						.build()
				)
				.user(u -> u
					.text(promptTemplate)
					.param("cv_data", cvContent)
					.param("output_format", cvOutputFormat)
				)
				.call()
				.content();

		} catch (Exception e) {
			log.error("Error while extracting CV from the content: {}", e.getMessage());
			return null;
		}
	}

	public AIAnalysisReportDetails generateReport(String cvDetails, String jobDescription) {
		var reportGenerationPrompt = this.qorvaPromptContextHolder.getReportGenerationPrompt();
		var reportOutputFormat = this.qorvaPromptContextHolder.getReportOutputFormat();
		var outputConverter = new BeanOutputConverter<>(CVScreeningReportOutputDTO.class);

		// Call the API
		var apiResponse = this.chatClient.prompt()
			.options(OpenAiChatOptions.builder()
				.model(GPT_5_MINI)
				.responseFormat(ResponseFormat
					.builder()
					.type(ResponseFormat.Type.JSON_SCHEMA)
					.jsonSchema(ResponseFormat.JsonSchema
						.builder()
						.name("report_generation")
						.schema(outputConverter.getJsonSchema())
						.strict(Boolean.TRUE)
						.build())
					.build()
				)
				.temperature(1.0)
				.build()
			)
			.user(u -> u
				.text(reportGenerationPrompt)
				.param("cv_data", cvDetails)
				.param("job_description", jobDescription)
				.param("output_format", reportOutputFormat)
			)
			.call()
			.content();

		// Assert response is not null
		Assert.notNull(apiResponse, "API response cannot be null");


		// Map the string content into CVScreeningReportDTO and render results
		return this.mapper.map(outputConverter.convert(apiResponse));
	}

	public ChatResult chatCompletions(String userMessage) throws QorvaException {
		var outputConverter = new BeanOutputConverter<>(OpenAIChatResponse.class);

		var response = this.chatClient.prompt()
			.options(OpenAiChatOptions.builder()
				.model(GPT_5_CHAT_LATEST)
				.responseFormat(ResponseFormat
					.builder()
					.type(ResponseFormat.Type.JSON_SCHEMA)
					.jsonSchema(ResponseFormat.JsonSchema
						.builder()
						.name("chat_completion")
						.schema(outputConverter.getJsonSchema())
						.strict(Boolean.TRUE)
						.build())
					.build()
				)
				.temperature(1.0)
				.build()
			)
			.user(u -> u.text(userMessage))
			.call()
			.content();

		// convert the response into ChatResponseDTO
		if (Objects.isNull(response)) {
			throw new QorvaException("Something went wrong. Please try again later");
		}

		return this.mapper.map(outputConverter.convert(response));
	}
}
