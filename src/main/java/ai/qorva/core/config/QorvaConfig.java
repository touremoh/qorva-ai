package ai.qorva.core.config;

import ai.qorva.core.dto.InsightIntent;
import ai.qorva.core.dto.QorvaPromptContextHolder;
import ai.qorva.core.exception.QorvaException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
@EnableConfigurationProperties(S3Properties.class)
@Configuration
public class QorvaConfig {

	@Bean
	S3Client s3Client(S3Properties s3Properties) {
		return S3Client.builder()
			.region(Region.of(s3Properties.getRegion()))
			.build();
	}

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
			var cvContentExtractionPrompt = this.readFile("CV_content_extraction_prompt.md");

			// Get CV output format
			var cvOutputFormat = this.readFile("CV_output_format.json");

			// Get Report generation message
			var reportGenerationPrompt = this.readFile("Matching_report_generation_prompt.md");

			// Get report output format
			var reportOutputFormat = this.readFile("Matching_report_response_format.json");

			// Get Library Insights prompts
			var intentClassifierPrompt = this.readFile("Intent_classifier_prompt.md");
			var insightAnswerGeneratorPrompt = this.readFile("Insight_answer_generator_prompt.md");
			var entityExtractorPrompts = Map.of(
				InsightIntent.TALENT_POOL_INTELLIGENCE, this.readFile("Entity_extractor_TALENT_POOL_INTELLIGENCE.md"),
				InsightIntent.SKILL_GAP_ANALYSIS,       this.readFile("Entity_extractor_SKILL_GAP_ANALYSIS.md"),
				InsightIntent.CANDIDATE_RANKING,        this.readFile("Entity_extractor_CANDIDATE_RANKING.md"),
				InsightIntent.CANDIDATE_REDISCOVERY,    this.readFile("Entity_extractor_CANDIDATE_REDISCOVERY.md"),
				InsightIntent.TALENT_CLUSTERING,        this.readFile("Entity_extractor_TALENT_CLUSTERING.md"),
				InsightIntent.SKILLS_DISTRIBUTION,      this.readFile("Entity_extractor_SKILLS_DISTRIBUTION.md"),
				InsightIntent.CANDIDATE_COMPARISON,     this.readFile("Entity_extractor_CANDIDATE_COMPARISON.md")
			);

			// Build and render results
			return QorvaPromptContextHolder
				.builder()
				.cvContentExtractionPromptTemplate(cvContentExtractionPrompt)
				.cvOutputFormat(cvOutputFormat)
				.reportGenerationPrompt(reportGenerationPrompt)
				.reportOutputFormat(reportOutputFormat)
				.intentClassifierPrompt(intentClassifierPrompt)
				.entityExtractorPrompts(entityExtractorPrompts)
				.insightAnswerGeneratorPrompt(insightAnswerGeneratorPrompt)
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

	@Bean
	public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatMultipartCustomizer() {
		return factory -> factory.addConnectorCustomizers(
			connector -> connector.setMaxPartCount(100)
		);
	}

	@Bean
	public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
		return builder -> builder.modules(new JavaTimeModule());
	}
}
