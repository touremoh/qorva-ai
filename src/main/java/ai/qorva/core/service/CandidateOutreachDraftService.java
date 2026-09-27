package ai.qorva.core.service;

import ai.qorva.core.service.orchestrators.StructuredOutput;

import ai.qorva.core.exception.QorvaErrors;

import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.CandidateOutreachData;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.MatchingReportDTO;
import ai.qorva.core.dto.common.KeySkill;
import ai.qorva.core.dto.common.Strength;
import ai.qorva.core.enums.OutreachIntentEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AI draft of a recruiter → candidate email. Same shape as {@link JobDescriptionBuilderService}:
 * one prompt file, placeholders, structured output, unmetered (drafting is free; the recruiter
 * edits and sends from their own mailbox).
 *
 * <p>The prompt only ever sees a slim slice of the profile — name, role, summary, top skills,
 * experience — never the whole CV document, and never contact details.</p>
 */
@Slf4j
@Service
public class CandidateOutreachDraftService {

	private static final int MAX_SKILLS = 8;
	private static final int MAX_JOB_DESCRIPTION_CHARS = 1500;
	private static final int MAX_STRENGTHS = 4;

	private final ChatClient chatClient;
	private final CVService cvService;
	private final JobPostService jobPostService;
	private final MatchingReportService matchingReportService;
	private final TenantService tenantService;
	private final UserRepository userRepository;
	private final String promptTemplate;

	/** Short personal prose with a small context — the mini tier is plenty. */
	@Value("${qorva.ai.outreach.model:gpt-4.1-mini}")
	private String model;

	public CandidateOutreachDraftService(ChatClient chatClient,
	                                     CVService cvService,
	                                     JobPostService jobPostService,
	                                     MatchingReportService matchingReportService,
	                                     TenantService tenantService,
	                                     UserRepository userRepository) throws QorvaException {
		this.chatClient = chatClient;
		this.cvService = cvService;
		this.jobPostService = jobPostService;
		this.matchingReportService = matchingReportService;
		this.tenantService = tenantService;
		this.userRepository = userRepository;
		this.promptTemplate = readPrompt();
	}

	public CandidateOutreachData.DraftResponse draft(String tenantId, String senderEmail,
	                                                  CandidateOutreachData.DraftRequest request,
	                                                  String fallbackLanguage) throws QorvaException {
		var intent = OutreachIntentEnum.fromValue(request.getIntent());
		if (intent == null) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.OUTREACH_INTENT_INVALID);
		}
		var language = StringUtils.hasText(request.getLanguage()) ? request.getLanguage() : fallbackLanguage;

		// findOneById is tenant-scoped through TenantContextHolder — a foreign id reads as not found.
		var cv = cvService.findOneById(request.getCvId());
		var job = StringUtils.hasText(request.getJobPostId()) ? jobPostService.findOneById(request.getJobPostId()) : null;
		var report = StringUtils.hasText(request.getMatchingReportId())
			? matchingReportService.findOneById(request.getMatchingReportId()) : null;

		var converter = new BeanOutputConverter<>(CandidateOutreachData.Draft.class);
		var prompt = buildPrompt(intent, language, request, cv, job, report,
			resolveSenderName(senderEmail), resolveCompanyName(tenantId), converter.getFormat());

		var content = chatClient.prompt()
			.options(OpenAiChatOptions.builder().model(model).temperature(temperatureFor(model)).build())
			.user(prompt)
			.call()
			.content();
		if (!StringUtils.hasText(content)) {
			throw draftFailed();
		}
		var draft = converter.convert(content);
		if (draft == null || !StringUtils.hasText(draft.getBody())) {
			throw draftFailed();
		}
		log.info("Outreach draft ({}, {}) for CV {} by {}", intent, language, request.getCvId(), senderEmail);
		return new CandidateOutreachData.DraftResponse(
			StringUtils.hasText(draft.getSubject()) ? draft.getSubject().strip() : "",
			draft.getBody().strip());
	}

	String buildPrompt(OutreachIntentEnum intent, String language, CandidateOutreachData.DraftRequest request,
	                   CVDTO cv, JobPostDTO job, MatchingReportDTO report,
	                   String senderName, String companyName, String format) {
		var info = cv.getPersonalInformation();
		var years = cv.getNbYearsOfExperience();
		var strengths = report != null && report.getMatchingReportDetails() != null
			? report.getMatchingReportDetails().getStrengths() : null;
		var headline = report != null && report.getMatchingReportDetails() != null
			&& report.getMatchingReportDetails().getDecisionSummary() != null
			? report.getMatchingReportDetails().getDecisionSummary().getReportHeadline() : null;

		return promptTemplate
			.replace("{format}", format)
			.replace("{intent}", intent.name())
			.replace("{language}", StringUtils.hasText(language) ? language : "en")
			.replace("{tone}", orEmpty(request.getTone()))
			.replace("{instructions}", orEmpty(request.getInstructions()))
			.replace("{candidate_name}", info != null ? orEmpty(info.getName()) : "")
			.replace("{candidate_role}", info != null ? orEmpty(info.getRole()) : "")
			.replace("{candidate_summary}", StringUtils.hasText(cv.getCandidateProfileSummary())
				? cv.getCandidateProfileSummary() : (info != null ? orEmpty(info.getSummary()) : ""))
			.replace("{candidate_skills}", topSkills(cv.getKeySkills()))
			.replace("{years_experience}", years != null ? String.valueOf(years) : "")
			.replace("{job_title}", job != null ? orEmpty(job.getTitle())
				: (report != null ? orEmpty(report.getJobPostTitle()) : ""))
			.replace("{job_description}", job != null ? truncate(orEmpty(job.getDescription()), MAX_JOB_DESCRIPTION_CHARS) : "")
			.replace("{report_headline}", orEmpty(headline))
			.replace("{report_strengths}", strengthTitles(strengths))
			.replace("{sender_name}", orEmpty(senderName))
			.replace("{company_name}", orEmpty(companyName));
	}

	static String topSkills(List<KeySkill> keySkills) {
		if (keySkills == null) return "";
		return keySkills.stream()
			.filter(Objects::nonNull)
			.flatMap(k -> k.getSkills() == null ? java.util.stream.Stream.<String>empty() : k.getSkills().stream())
			.filter(StringUtils::hasText)
			.distinct()
			.limit(MAX_SKILLS)
			.collect(Collectors.joining(", "));
	}

	static String strengthTitles(List<Strength> strengths) {
		if (strengths == null) return "";
		return strengths.stream()
			.filter(Objects::nonNull)
			.map(Strength::getTitle)
			.filter(StringUtils::hasText)
			.limit(MAX_STRENGTHS)
			.collect(Collectors.joining("; "));
	}

	/** GPT-5-family models only accept the default temperature (1); mini/4.x models take a lower one. */
	static double temperatureFor(String model) {
		return StructuredOutput.temperatureFor(model, 0.7);
	}

	private String resolveSenderName(String email) {
		var user = userRepository.findByEmail(email);
		if (user == null) return "";
		var name = ((StringUtils.hasText(user.getFirstName()) ? user.getFirstName() : "") + " "
			+ (StringUtils.hasText(user.getLastName()) ? user.getLastName() : "")).trim();
		return name;
	}

	private String resolveCompanyName(String tenantId) {
		try {
			var name = tenantService.findOneById(tenantId).getTenantName();
			return name != null ? name : "";
		} catch (Exception e) {
			return "";
		}
	}

	private static String truncate(String value, int max) {
		return value.length() <= max ? value : value.substring(0, max) + " …";
	}

	private static String orEmpty(String value) {
		return value != null ? value : "";
	}

	private static QorvaException draftFailed() {
		return new QorvaException(QorvaErrorCodes.OUTREACH_DRAFT_FAILED,
			HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR);
	}

	private String readPrompt() throws QorvaException {
		try (var reader = new BufferedReader(new InputStreamReader(
			new ClassPathResource("prompts/Candidate_outreach_prompt.md").getInputStream(), StandardCharsets.UTF_8))) {
			return reader.lines().collect(Collectors.joining("\n"));
		} catch (Exception e) {
			throw new QorvaException("Cannot read candidate outreach prompt", e);
		}
	}
}
