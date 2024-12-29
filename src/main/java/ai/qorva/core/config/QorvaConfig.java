package ai.qorva.core.config;

import ai.qorva.core.dto.QorvaPromptContextHolder;
import ai.qorva.core.exception.QorvaException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.reactive.config.EnableWebFlux;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Configuration
public class QorvaConfig {

	@Bean
	ChatClient chatClient(ChatClient.Builder builder) {
		return builder
			.defaultSystem("You are a CV screening expert that answer questions about screening CVs and candidates skills evaluation in different domains")
			.build();
	}

	@Bean
	public QorvaPromptContextHolder getPromptContextHolder() throws QorvaException {
		try {
			// Get CV content extraction message
			var cvContentExtractionPrompt = this.readFile("CV_content_extraction_prompt.txt");

			// Get CV output format
			var cvOutputFormat = this.readFile("CV_output_format.json");

			// Get Report generation message
			var reportGenerationPrompt = this.readFile("Report_generation_prompt.txt");

			// Get report output format
			var reportOutputFormat = this.readFile("Report_output_format.json");

			// Build and render results
			return QorvaPromptContextHolder
				.builder()
				.cvContentExtractionPromptTemplate(cvContentExtractionPrompt)
				.cvOutputFormat(cvOutputFormat)
				.reportGenerationPrompt(reportGenerationPrompt)
				.reportOutputFormat(reportOutputFormat)
				.build();
		} catch (IOException e) {
			throw new QorvaException("Unable to read file", e);
		}
	}

	protected String readFile(String fileName) throws IOException {
		// Access the file
		ClassPathResource resource = new ClassPathResource("prompts/" + fileName);
		// Read file content as a string
		try (BufferedReader reader = new BufferedReader(
			new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

			StringBuilder content = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				content.append(line).append("\n");
			}
			return content.toString().trim();
		}
	}
}
