package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.entity.JobPost;
import ai.qorva.core.dto.AnswerGenerationResult;
import ai.qorva.core.dto.InsightHandlerResult;
import ai.qorva.core.dto.InsightIntent;
import ai.qorva.core.dto.QorvaPromptContextHolder;
import ai.qorva.core.dto.common.WorkExperience;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.ai.openai.api.OpenAiApi.ChatModel.GPT_4_1_MINI;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsightAnswerGenerator {

	private final ChatClient chatClient;
	private final QorvaPromptContextHolder promptContextHolder;
	private final ObjectMapper objectMapper;

	public AnswerGenerationResult generate(InsightHandlerResult result, InsightIntent intent, String originalQuestion) {
		return generate(result, intent, originalQuestion, null);
	}

	public AnswerGenerationResult generate(
			InsightHandlerResult result,
			InsightIntent intent,
			String originalQuestion,
			MentionResolver.ResolvedMentions resolvedMentions
	) {
		var converter = new BeanOutputConverter<>(AnswerGenerationResult.class);
		var promptTemplate = promptContextHolder.getInsightAnswerGeneratorPrompt();

		try {
			String resultJson = objectMapper.writeValueAsString(result);
			String mentionContextJson = buildMentionContextJson(resolvedMentions);

			String renderedPrompt = promptTemplate
				.replace("{{intent}}", intent.name())
				.replace("{{question}}", originalQuestion)
				.replace("{{handler_result_json}}", resultJson)
				.replace("{{mention_context}}", mentionContextJson);

			String content = chatClient.prompt()
				.options(OpenAiChatOptions.builder()
					.model(GPT_4_1_MINI)
					.responseFormat(ResponseFormat.builder()
						.type(ResponseFormat.Type.JSON_SCHEMA)
						.jsonSchema(ResponseFormat.JsonSchema.builder()
							.name("insight_answer")
							.schema(converter.getJsonSchema())
							.strict(Boolean.FALSE)
							.build())
						.build())
					.temperature(0.3)
					.build())
				.messages(new UserMessage(renderedPrompt))
				.call()
				.content();

			return objectMapper.readValue(content, AnswerGenerationResult.class);
		} catch (Exception e) {
			log.error("Error generating insight answer: {}", e.getMessage());
			return new AnswerGenerationResult(
				null,
				"I was unable to generate a summary at this time. Please try again.",
				List.of(),
				null
			);
		}
	}

	private String buildMentionContextJson(MentionResolver.ResolvedMentions resolved) throws Exception {
		if (resolved == null || resolved.isEmpty()) {
			return "{}";
		}
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("candidates", resolved.candidates().stream().map(this::summarizeCandidate).toList());
		payload.put("jobs", resolved.jobs().stream().map(this::summarizeJob).toList());
		return objectMapper.writeValueAsString(payload);
	}

	private Map<String, Object> summarizeCandidate(CV cv) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("id", cv.getId());
		m.put("name", cv.getPersonalInformation() != null ? cv.getPersonalInformation().getName() : null);
		m.put("yearsOfExperience", cv.getNbYearsOfExperience());
		m.put("profileSummary", cv.getCandidateProfileSummary());
		m.put("keySkills", cv.getKeySkills());
		m.put("workExperience", cv.getWorkExperience() != null
			? cv.getWorkExperience().stream().map(this::summarizeWorkExperience).toList()
			: List.of());
		m.put("education", cv.getEducation());
		m.put("candidateClustering", cv.getCandidateClustering());
		m.put("tags", cv.getTags());
		return m;
	}

	private Map<String, Object> summarizeWorkExperience(WorkExperience we) {
		if (we == null) return Map.of();
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("position", we.getPosition());
		m.put("company", we.getCompany());
		m.put("location", we.getLocation());
		m.put("from", we.getFrom());
		m.put("to", we.getTo());
		return m;
	}

	private Map<String, Object> summarizeJob(JobPost job) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("id", job.getId());
		m.put("title", job.getTitle());
		m.put("jobReference", job.getJobReference());
		m.put("description", job.getDescription());
		m.put("status", job.getStatus());
		return m;
	}
}
