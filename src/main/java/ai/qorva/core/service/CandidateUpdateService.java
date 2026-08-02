package ai.qorva.core.service;

import ai.qorva.core.dao.entity.CandidateUpdateRequest;
import ai.qorva.core.dao.entity.SuppressedEmail;
import ai.qorva.core.dao.repository.CandidateUpdateRequestRepository;
import ai.qorva.core.dao.repository.SuppressedEmailRepository;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.CandidateUpdateData;
import ai.qorva.core.dto.common.Availability;
import ai.qorva.core.dto.common.SalaryExpectation;
import ai.qorva.core.enums.ContentDateSourceEnum;
import ai.qorva.core.exception.QorvaException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * Candidate self-update flow: tokenized invitations, the hardened public form backend,
 * and the do-not-contact suppression list. Completing an update sets VERIFIED freshness —
 * the strongest evidence there is.
 */
@Slf4j
@Service
public class CandidateUpdateService {

	private static final Duration TOKEN_VALIDITY = Duration.ofDays(30);
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final CandidateUpdateRequestRepository requestRepository;
	private final SuppressedEmailRepository suppressedEmailRepository;
	private final CVService cvService;
	private final LibraryQualityCacheEvictor cacheEvictor;
	private final S3StorageService s3StorageService;
	private final ObjectMapper objectMapper;

	@Value("${weblink.appBaseUrl}")
	private String appBaseUrl;

	// SUBMIT_FAILED is deliberately "active": the token was never consumed, the candidate may retry.
	private static final List<String> ACTIVE_STATUSES = List.of(
		CandidateUpdateRequest.STATUS_SENT,
		CandidateUpdateRequest.STATUS_OPENED,
		CandidateUpdateRequest.STATUS_SUBMITTED,
		CandidateUpdateRequest.STATUS_PROCESSING,
		CandidateUpdateRequest.STATUS_SUBMIT_FAILED);

	@Autowired
	public CandidateUpdateService(
		CandidateUpdateRequestRepository requestRepository,
		SuppressedEmailRepository suppressedEmailRepository,
		CVService cvService,
		LibraryQualityCacheEvictor cacheEvictor,
		S3StorageService s3StorageService,
		ObjectMapper objectMapper
	) {
		this.requestRepository = requestRepository;
		this.suppressedEmailRepository = suppressedEmailRepository;
		this.cvService = cvService;
		this.cacheEvictor = cacheEvictor;
		this.s3StorageService = s3StorageService;
		this.objectMapper = objectMapper;
	}

	public boolean isSuppressed(String tenantId, String email) {
		return suppressedEmailRepository.existsByTenantIdAndEmail(tenantId, email.toLowerCase());
	}

	public boolean hasActiveRequest(String tenantId, String cvId) {
		return requestRepository.existsByTenantIdAndCvIdAndStatusIn(tenantId, cvId, ACTIVE_STATUSES);
	}

	/** Creates a request and returns the PLAINTEXT token (only ever exists in the emailed link). */
	public String createRequest(String tenantId, String cvId, String candidateEmail, String language) {
		var tokenBytes = new byte[32];
		SECURE_RANDOM.nextBytes(tokenBytes);
		var token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

		requestRepository.save(CandidateUpdateRequest.builder()
			.tenantId(tenantId)
			.cvId(cvId)
			.candidateEmail(candidateEmail.toLowerCase())
			.tokenHash(sha256(token))
			.status(CandidateUpdateRequest.STATUS_SENT)
			.language(language)
			.sentAt(Instant.now())
			.expiresAt(Instant.now().plus(TOKEN_VALIDITY))
			.build());
		return token;
	}

	public String buildUpdateLink(String token) {
		return appBaseUrl + "/candidate-update/" + token;
	}

	public CandidateUpdateData.Prefill getPrefill(String token) throws QorvaException {
		var request = findValidRequest(token);
		if (CandidateUpdateRequest.STATUS_SENT.equals(request.getStatus())) {
			request.setStatus(CandidateUpdateRequest.STATUS_OPENED);
			request.setOpenedAt(Instant.now());
			requestRepository.save(request);
		}

		var cv = loadCv(request);
		var pi = cv.getPersonalInformation();
		var availability = pi != null ? pi.getAvailability() : null;
		var salary = cv.getSalaryExpectation();
		var fullName = pi != null && StringUtils.hasText(pi.getName()) ? pi.getName() : "";
		var firstName = fullName.contains(" ") ? fullName.substring(0, fullName.indexOf(' ')) : fullName;

		return new CandidateUpdateData.Prefill(
			firstName,
			availability != null ? availability.getStatus() : null,
			availability != null ? availability.getAvailableFrom() : null,
			availability != null ? availability.getNoticePeriodDays() : null,
			availability != null ? availability.getOpenToWork() : null,
			salary != null ? salary.getCurrency() : null,
			salary != null ? salary.getMin() : null,
			salary != null ? salary.getMax() : null,
			request.getLanguage());
	}

	/** Applies the candidate's update; an optional newer CV file replaces the old document entirely. */
	public void complete(String token, CandidateUpdateData.Submission submission, MultipartFile newCvFile) throws QorvaException {
		var request = findValidRequest(token);
		var tenantId = request.getTenantId();
		var cvId = request.getCvId();

		if (newCvFile != null && !newCvFile.isEmpty()) {
			// Newer document: run it through the normal ingest pipeline, then replace the old copy.
			var created = cvService.processFile(newCvFile, tenantId);
			cvService.replaceDuplicate(created.getId(), cvId, tenantId);
			cvId = created.getId();
		}

		applyAndComplete(request, submission, cvId);
	}

	/**
	 * Stages a file-carrying submission for asynchronous processing: file to S3, fields
	 * onto the request, status SUBMITTED. Returns immediately — the request thread never
	 * runs the LLM extraction.
	 */
	public void enqueue(String token, CandidateUpdateData.Submission submission, MultipartFile file) throws QorvaException {
		var request = findValidRequest(token);
		validateSubmittedFile(file);

		String payload;
		try {
			payload = objectMapper.writeValueAsString(
				submission != null ? submission : new CandidateUpdateData.Submission(null, null, null, null, null, null, null));
		} catch (Exception e) {
			throw new QorvaException("Invalid submission", e,
				HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
		}

		var fileKey = s3StorageService.uploadCandidateSubmission(request.getTenantId(), request.getId(), file);

		request.setSubmissionPayload(payload);
		request.setPendingFileKey(fileKey);
		request.setOriginalFileName(file.getOriginalFilename());
		request.setStatus(CandidateUpdateRequest.STATUS_SUBMITTED);
		request.setSubmittedAt(Instant.now());
		request.setProcessingStage(null);
		request.setProcessingError(null);
		requestRepository.save(request);
		log.info("Candidate submission staged for CV {} (tenant {}, request {})",
			request.getCvId(), request.getTenantId(), request.getId());
	}

	/**
	 * Async-submission progress for the public polling endpoint. Unlike
	 * {@link #findValidRequest}, COMPLETED is visible here — the final poll must see DONE.
	 */
	public CandidateUpdateData.StatusView status(String token) throws QorvaException {
		var request = findRequest(token);
		var state = switch (request.getStatus()) {
			case CandidateUpdateRequest.STATUS_SUBMITTED -> "SUBMITTED";
			case CandidateUpdateRequest.STATUS_PROCESSING ->
				CandidateUpdateRequest.STAGE_UPDATING.equals(request.getProcessingStage()) ? "UPDATING" : "PARSING";
			case CandidateUpdateRequest.STATUS_COMPLETED -> "DONE";
			case CandidateUpdateRequest.STATUS_SUBMIT_FAILED -> "FAILED";
			default -> null;   // SENT/OPENED/EXPIRED: nothing was submitted — uniform 404
		};
		if (state == null) {
			throw notFound();
		}
		return new CandidateUpdateData.StatusView(state);
	}

	/**
	 * Worker step after the staged file was parsed into a new CV: replace the old
	 * document, apply the submitted fields, mark COMPLETED.
	 */
	public void finalizeStagedSubmission(CandidateUpdateRequest request, String newCvId) throws QorvaException {
		cvService.replaceDuplicate(newCvId, request.getCvId(), request.getTenantId());
		applyAndComplete(request, readSubmissionPayload(request), newCvId);
	}

	public CandidateUpdateData.Submission readSubmissionPayload(CandidateUpdateRequest request) throws QorvaException {
		if (!StringUtils.hasText(request.getSubmissionPayload())) {
			return null;
		}
		try {
			return objectMapper.readValue(request.getSubmissionPayload(), CandidateUpdateData.Submission.class);
		} catch (Exception e) {
			throw new QorvaException("Corrupt submission payload for request " + request.getId(), e,
				HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	private void applyAndComplete(CandidateUpdateRequest request, CandidateUpdateData.Submission submission, String cvId)
		throws QorvaException {
		var cv = cvService.findOneById(cvId);
		applySubmission(cv, submission);
		cv.setContentDate(Instant.now());
		cv.setContentDateSource(ContentDateSourceEnum.VERIFIED.name());
		cvService.updateOne(cvId, cv);

		request.setStatus(CandidateUpdateRequest.STATUS_COMPLETED);
		request.setCompletedAt(Instant.now());
		request.setPendingFileKey(null);
		request.setProcessingStage(null);
		request.setProcessingError(null);
		requestRepository.save(request);
		cacheEvictor.evict(request.getTenantId());
		log.info("Candidate update completed for CV {} (tenant {})", cvId, request.getTenantId());
	}

	/** Fail fast at submit time — the ingest pipeline only reads .pdf/.docx. */
	private void validateSubmittedFile(MultipartFile file) throws QorvaException {
		var name = file.getOriginalFilename();
		var lower = name != null ? name.toLowerCase() : "";
		if (!lower.endsWith(".pdf") && !lower.endsWith(".docx")) {
			throw new QorvaException("Unsupported file type — please upload a .pdf or .docx resume",
				HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
		}
	}

	public void unsubscribe(String token) throws QorvaException {
		var request = findRequest(token);   // unsubscribe honored even after expiry
		if (!suppressedEmailRepository.existsByTenantIdAndEmail(request.getTenantId(), request.getCandidateEmail())) {
			suppressedEmailRepository.save(SuppressedEmail.builder()
				.tenantId(request.getTenantId())
				.email(request.getCandidateEmail())
				.reason(SuppressedEmail.REASON_UNSUBSCRIBED)
				.createdAt(Instant.now())
				.build());
		}
	}

	// -------------------------------------------------------------------------

	private void applySubmission(CVDTO cv, CandidateUpdateData.Submission submission) {
		if (submission == null) return;
		if (cv.getPersonalInformation() == null) {
			cv.setPersonalInformation(new ai.qorva.core.dto.common.PersonalInformation());
		}
		var availability = cv.getPersonalInformation().getAvailability() != null
			? cv.getPersonalInformation().getAvailability() : new Availability();
		if (submission.availabilityStatus() != null) availability.setStatus(submission.availabilityStatus());
		if (submission.availableFrom() != null) availability.setAvailableFrom(submission.availableFrom());
		if (submission.noticePeriodDays() != null) availability.setNoticePeriodDays(submission.noticePeriodDays());
		if (submission.openToWork() != null) availability.setOpenToWork(submission.openToWork());
		cv.getPersonalInformation().setAvailability(availability);

		if (submission.salaryCurrency() != null || submission.salaryMin() != null || submission.salaryMax() != null) {
			var salary = cv.getSalaryExpectation() != null ? cv.getSalaryExpectation() : new SalaryExpectation();
			if (submission.salaryCurrency() != null) salary.setCurrency(submission.salaryCurrency());
			if (submission.salaryMin() != null) salary.setMin(submission.salaryMin());
			if (submission.salaryMax() != null) salary.setMax(submission.salaryMax());
			cv.setSalaryExpectation(salary);
		}
	}

	private CandidateUpdateRequest findValidRequest(String token) throws QorvaException {
		var request = findRequest(token);
		if (CandidateUpdateRequest.STATUS_COMPLETED.equals(request.getStatus())) {
			throw notFound();   // single-use — completed links behave as gone
		}
		// A submission is already queued or in flight — no second enqueue (double-click guard).
		if (CandidateUpdateRequest.STATUS_SUBMITTED.equals(request.getStatus())
			|| CandidateUpdateRequest.STATUS_PROCESSING.equals(request.getStatus())) {
			throw notFound();
		}
		// SUBMIT_FAILED falls through: the token was never consumed, the candidate may retry.
		if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(Instant.now())) {
			request.setStatus(CandidateUpdateRequest.STATUS_EXPIRED);
			requestRepository.save(request);
			throw notFound();
		}
		return request;
	}

	private CandidateUpdateRequest findRequest(String token) throws QorvaException {
		if (!StringUtils.hasText(token) || token.length() > 128) {
			throw notFound();
		}
		return requestRepository.findByTokenHash(sha256(token)).orElseThrow(this::notFound);
	}

	private CVDTO loadCv(CandidateUpdateRequest request) throws QorvaException {
		// No tenant context on public calls — CVService skips the tenant assert; the token IS the authorization.
		return cvService.findOneById(request.getCvId());
	}

	/** Deliberately generic — public endpoints must not reveal whether a token ever existed. */
	private QorvaException notFound() {
		return new QorvaException("Link not found or no longer valid",
			HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND);
	}

	static String sha256(String value) {
		try {
			var digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
