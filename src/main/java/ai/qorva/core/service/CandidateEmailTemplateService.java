package ai.qorva.core.service;

import ai.qorva.core.dao.entity.CandidateEmailTemplate;
import ai.qorva.core.dao.repository.CandidateEmailTemplateRepository;
import ai.qorva.core.dto.CandidateEmailTemplateData;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * CRUD + rendering for recruiter-authored candidate-update invitation templates.
 * Templates are plain text with a closed set of placeholders; rendering (escaping,
 * paragraph handling, HTML shell) is delegated to {@link CandidateUpdateEmailService}
 * so preview/test-send show exactly what a campaign will send.
 */
@Slf4j
@Service
public class CandidateEmailTemplateService {

	private static final int NAME_MAX = 80;
	private static final int SUBJECT_MAX = 150;
	private static final int BODY_MAX = 4000;
	private static final String SAMPLE_CANDIDATE_NAME = "Alex";

	private static final Set<String> ALLOWED_PLACEHOLDERS = Set.of(
		CandidateUpdateEmailService.PLACEHOLDER_CANDIDATE_NAME,
		CandidateUpdateEmailService.PLACEHOLDER_COMPANY_NAME);

	private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^{}]*)}}");

	private final CandidateEmailTemplateRepository templateRepository;
	private final CandidateUpdateEmailService emailService;
	private final TenantService tenantService;
	private final UserService userService;
	private final ProductReferenceService productReferenceService;

	@Value("${weblink.appBaseUrl}")
	private String appBaseUrl;

	@Autowired
	public CandidateEmailTemplateService(
		CandidateEmailTemplateRepository templateRepository,
		CandidateUpdateEmailService emailService,
		TenantService tenantService,
		UserService userService,
		ProductReferenceService productReferenceService
	) {
		this.templateRepository = templateRepository;
		this.emailService = emailService;
		this.tenantService = tenantService;
		this.userService = userService;
		this.productReferenceService = productReferenceService;
	}

	public CandidateEmailTemplateData.TemplateList list(String tenantId) {
		var templates = templateRepository.findByTenantIdOrderByNameAsc(tenantId).stream()
			.map(CandidateEmailTemplateData.TemplateView::from)
			.toList();
		return new CandidateEmailTemplateData.TemplateList(templates, resolveTemplateLimit(tenantId));
	}

	public CandidateEmailTemplateData.TemplateView create(String tenantId, String createdBy,
		CandidateEmailTemplateData.SaveRequest request) throws QorvaException {
		validate(request);
		enforcePlanLimit(tenantId);
		if (templateRepository.existsByTenantIdAndName(tenantId, request.name().trim())) {
			throw badRequest("A template with this name already exists");
		}
		var now = Instant.now();
		var saved = templateRepository.save(CandidateEmailTemplate.builder()
			.tenantId(tenantId)
			.name(request.name().trim())
			.subject(request.subject().trim())
			.bodyText(request.bodyText())
			.createdBy(createdBy)
			.createdAt(now)
			.updatedAt(now)
			.build());
		log.info("Candidate email template {} created for tenant {}", saved.getId(), tenantId);
		return CandidateEmailTemplateData.TemplateView.from(saved);
	}

	public CandidateEmailTemplateData.TemplateView update(String tenantId, String templateId,
		CandidateEmailTemplateData.SaveRequest request) throws QorvaException {
		validate(request);
		var template = findOwned(tenantId, templateId);
		var newName = request.name().trim();
		if (!newName.equals(template.getName()) && templateRepository.existsByTenantIdAndName(tenantId, newName)) {
			throw badRequest("A template with this name already exists");
		}
		template.setName(newName);
		template.setSubject(request.subject().trim());
		template.setBodyText(request.bodyText());
		template.setUpdatedAt(Instant.now());
		return CandidateEmailTemplateData.TemplateView.from(templateRepository.save(template));
	}

	public void delete(String tenantId, String templateId) throws QorvaException {
		templateRepository.delete(findOwned(tenantId, templateId));
		log.info("Candidate email template {} deleted for tenant {}", templateId, tenantId);
	}

	/** Renders a draft (saved or not) inside the production HTML shell with sample data. */
	public CandidateEmailTemplateData.PreviewResponse preview(String tenantId, String callerEmail,
		CandidateEmailTemplateData.PreviewRequest request) throws QorvaException {
		validateContent(request.subject(), request.bodyText());
		var rendered = emailService.buildInvitation(
			SAMPLE_CANDIDATE_NAME,
			tenantName(tenantId),
			previewLink(),
			request.language(),
			new CandidateUpdateEmailService.CustomTemplate(request.subject(), request.bodyText()),
			resolveSenderName(callerEmail));
		return new CandidateEmailTemplateData.PreviewResponse(rendered.subject(), rendered.html());
	}

	/** Sends the template to the requesting user's own address — insurance before a bulk campaign. */
	public void sendTest(String tenantId, String templateId, String recipientEmail, String language) throws QorvaException {
		var template = findOwned(tenantId, templateId);
		emailService.sendUpdateInvitation(
			recipientEmail,
			SAMPLE_CANDIDATE_NAME,
			tenantName(tenantId),
			previewLink(),
			language,
			new CandidateUpdateEmailService.CustomTemplate(template.getSubject(), template.getBodyText()),
			resolveSenderName(recipientEmail));
		log.info("Test send of template {} to {} (tenant {})", templateId, recipientEmail, tenantId);
	}

	/**
	 * Preview/test sign with the caller's own name; live campaigns sign with the name of
	 * whoever launched the campaign (resolved in BackgroundJobWorker from job.createdBy).
	 */
	private String resolveSenderName(String email) {
		if (!StringUtils.hasText(email)) {
			return null;
		}
		try {
			var user = userService.findByEmail(email);
			if (user == null) {
				return null;
			}
			var fullName = ((user.getFirstName() != null ? user.getFirstName() : "") + " "
				+ (user.getLastName() != null ? user.getLastName() : "")).trim();
			return fullName.isEmpty() ? null : fullName;
		} catch (Exception e) {
			log.warn("Could not resolve sender name for {}: {}", email, e.getMessage());
			return null;
		}
	}

	/** Used by campaign submission to snapshot subject/body onto the job. */
	public CandidateEmailTemplate findOwned(String tenantId, String templateId) throws QorvaException {
		return templateRepository.findByIdAndTenantId(templateId, tenantId)
			.orElseThrow(() -> new QorvaException("Template not found",
				HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND));
	}

	// -------------------------------------------------------------------------

	private void validate(CandidateEmailTemplateData.SaveRequest request) throws QorvaException {
		if (request == null || !StringUtils.hasText(request.name())) {
			throw badRequest("Template name is required");
		}
		if (request.name().trim().length() > NAME_MAX) {
			throw badRequest("Template name must be at most " + NAME_MAX + " characters");
		}
		validateContent(request.subject(), request.bodyText());
	}

	private void validateContent(String subject, String bodyText) throws QorvaException {
		if (!StringUtils.hasText(subject)) {
			throw badRequest("Subject is required");
		}
		if (subject.trim().length() > SUBJECT_MAX) {
			throw badRequest("Subject must be at most " + SUBJECT_MAX + " characters");
		}
		if (!StringUtils.hasText(bodyText)) {
			throw badRequest("Message body is required");
		}
		if (bodyText.length() > BODY_MAX) {
			throw badRequest("Message body must be at most " + BODY_MAX + " characters");
		}
		validatePlaceholders(subject);
		validatePlaceholders(bodyText);
	}

	private void validatePlaceholders(String text) throws QorvaException {
		var matcher = PLACEHOLDER_PATTERN.matcher(text);
		while (matcher.find()) {
			var token = matcher.group(1).trim();
			if (!ALLOWED_PLACEHOLDERS.contains(token)) {
				throw badRequest("Unknown placeholder {{" + token + "}} — allowed: {{candidate_name}}, {{company_name}}");
			}
		}
	}

	/**
	 * Plan cap on saved templates ({@code features.limits.emailTemplates} on the tenant's
	 * product). Unresolvable plan or null limit → unlimited, consistent with how usage
	 * periods treat missing limits.
	 */
	private void enforcePlanLimit(String tenantId) throws QorvaException {
		var limit = resolveTemplateLimit(tenantId);
		if (limit != null && templateRepository.countByTenantId(tenantId) >= limit) {
			throw badRequest("Your plan allows up to " + limit
				+ " email templates — delete one or upgrade to create more");
		}
	}

	/** Plan cap on saved templates; null → unlimited (also on unresolvable plan). */
	private Integer resolveTemplateLimit(String tenantId) {
		try {
			var sub = tenantService.findOneById(tenantId).getSubscriptionInfo();
			if (sub != null && StringUtils.hasText(sub.getPriceId())) {
				var product = productReferenceService.findByStripePriceId(sub.getPriceId());
				if (product != null && product.getFeatures() != null && product.getFeatures().getLimits() != null) {
					return product.getFeatures().getLimits().getEmailTemplates();
				}
			}
		} catch (Exception e) {
			log.warn("Could not resolve email-template limit for tenant {}: {}", tenantId, e.getMessage());
		}
		return null;
	}

	private String tenantName(String tenantId) throws QorvaException {
		return tenantService.findOneById(tenantId).getTenantName();
	}

	/** Dead link: resolves to the app's uniform invalid-token page, never a live token. */
	private String previewLink() {
		return appBaseUrl + "/candidate-update/preview";
	}

	private QorvaException badRequest(String message) {
		return new QorvaException(message, HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
	}
}
