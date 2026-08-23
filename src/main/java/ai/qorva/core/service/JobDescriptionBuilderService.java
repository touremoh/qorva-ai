package ai.qorva.core.service;

import ai.qorva.core.dto.JobDescriptionData;
import ai.qorva.core.dto.common.ScoringRules;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * AI job-description builder: a few structured inputs become a ready-to-edit job
 * description plus suggested scoring rules (chained through the existing prefill
 * service, unmetered — this feature is free; screening actions are billed where the
 * real cost is, at screening time).
 */
@Slf4j
@Service
public class JobDescriptionBuilderService {

	private final ChatClient chatClient;
	private final ScoringRulesPrefillService scoringRulesPrefillService;
	private final TenantService tenantService;
	private final String promptTemplate;

	/** JD writing is a fluency task — mini tier earns its keep here (same as report generation). */
	@Value("${qorva.ai.job-description.model:gpt-5-mini}")
	private String model;

	public JobDescriptionBuilderService(ChatClient chatClient,
	                                    ScoringRulesPrefillService scoringRulesPrefillService,
	                                    TenantService tenantService) throws QorvaException {
		this.chatClient = chatClient;
		this.scoringRulesPrefillService = scoringRulesPrefillService;
		this.tenantService = tenantService;
		this.promptTemplate = readPrompt();
	}

	public JobDescriptionData.GenerateResponse generate(String tenantId, JobDescriptionData.GenerateRequest request,
	                                                    String fallbackLanguage) throws QorvaException {
		var language = StringUtils.hasText(request.getLanguage()) ? request.getLanguage() : fallbackLanguage;
		var converter = new BeanOutputConverter<>(JobDescriptionData.Draft.class);

		var prompt = promptTemplate
			.replace("{format}", converter.getFormat())
			.replace("{job_title}", orEmpty(request.getTitle()))
			.replace("{seniority}", orEmpty(request.getSeniority()))
			.replace("{must_have_skills}", orEmpty(request.getMustHaveSkills()))
			.replace("{nice_to_have_skills}", orEmpty(request.getNiceToHaveSkills()))
			.replace("{location}", orEmpty(request.getLocation()))
			.replace("{contract_type}", orEmpty(request.getContractType()))
			.replace("{company_name}", resolveCompanyName(tenantId))
			.replace("{language}", StringUtils.hasText(language) ? language : "en")
			.replace("{tone}", orEmpty(request.getTone()))
			.replace("{extra_notes}", orEmpty(request.getExtraNotes()));

		var content = chatClient.prompt()
			.options(OpenAiChatOptions.builder().model(model).build())
			.user(prompt)
			.call()
			.content();
		if (!StringUtils.hasText(content)) {
			throw new QorvaException("Job description generation failed",
				HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
		var draft = converter.convert(content);
		if (draft == null || !StringUtils.hasText(draft.getDescription())) {
			throw new QorvaException("Job description generation failed",
				HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR);
		}

		// Best-effort chaining: a failed scoring-rules leg must not lose the draft.
		ScoringRules rules = null;
		try {
			rules = scoringRulesPrefillService.suggestUnmetered(draft.getTitle(), draft.getDescription());
		} catch (Exception e) {
			log.warn("Scoring-rules suggestion failed for generated JD (tenant {}): {}", tenantId, e.getMessage());
		}

		return new JobDescriptionData.GenerateResponse(
			StringUtils.hasText(draft.getTitle()) ? draft.getTitle() : request.getTitle(),
			draft.getDescription(),
			rules);
	}

	private String resolveCompanyName(String tenantId) {
		try {
			var name = tenantService.findOneById(tenantId).getTenantName();
			return name != null ? name : "";
		} catch (Exception e) {
			return "";
		}
	}

	private static String orEmpty(String value) {
		return value != null ? value : "";
	}

	private String readPrompt() throws QorvaException {
		try (var reader = new BufferedReader(new InputStreamReader(
			new ClassPathResource("prompts/Job_description_builder_prompt.md").getInputStream(), StandardCharsets.UTF_8))) {
			return reader.lines().collect(Collectors.joining("\n"));
		} catch (Exception e) {
			throw new QorvaException("Cannot read job description builder prompt", e);
		}
	}
}
