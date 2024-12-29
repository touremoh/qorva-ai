package ai.qorva.core.service;

import ai.qorva.core.dto.*;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.OpenAIResultMapper;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.Binary;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.*;

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

		// Set temperate at 0.5 to reduce randomness
		double temperature = 0.5;

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

	public CVScreeningReportDTO match(String listOfCVs, String jobDescription, String language) {
		var outputConverter = new BeanOutputConverter<>(CVScreeningReportOutputDTO.class);

		// map content into CV DTO and return
		var flux = this.streamReport(listOfCVs, jobDescription, language, outputConverter.getJsonSchema());

		var content = String.join("", Objects.requireNonNull(flux.collectList().block()));

		return this.mapper.map(outputConverter.convert(content));

	}

	protected Flux<String> streamReport(String listOfCVs, String jobDescription, String language, String jsonSchema) {
		var reportGenerationPrompt = this.qorvaPromptContextHolder.getReportGenerationPrompt();
		var reportOutputFormat = this.qorvaPromptContextHolder.getReportOutputFormat();

		double temperature = 0.5;

		return this.chatClient.prompt()
			.options(OpenAiChatOptions
				.builder()
				.withModel(GPT_4_O_MINI)
				.withResponseFormat(new ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, jsonSchema))
				.withTemperature(temperature)
				.withMaxTokens(20000)
				.build()
			)
			.user(u -> u
				.text(reportGenerationPrompt)
				.param("cv_data", listOfCVs)
				.param("job_description", jobDescription)
				.param("output_format", reportOutputFormat)
				.param("language", language)
			)
			.stream()
			.content();
	}
}
