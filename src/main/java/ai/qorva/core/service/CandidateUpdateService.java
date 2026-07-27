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

	@Value("${weblink.appBaseUrl}")
	private String appBaseUrl;

	private static final List<String> ACTIVE_STATUSES =
		List.of(CandidateUpdateRequest.STATUS_SENT, CandidateUpdateRequest.STATUS_OPENED);

	@Autowired
	public CandidateUpdateService(
		CandidateUpdateRequestRepository requestRepository,
		SuppressedEmailRepository suppressedEmailRepository,
		CVService cvService,
		LibraryQualityCacheEvictor cacheEvictor
	) {
		this.requestRepository = requestRepository;
		this.suppressedEmailRepository = suppressedEmailRepository;
		this.cvService = cvService;
		this.cacheEvictor = cacheEvictor;
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

		var cv = cvService.findOneById(cvId);
		applySubmission(cv, submission);
		cv.setContentDate(Instant.now());
		cv.setContentDateSource(ContentDateSourceEnum.VERIFIED.name());
		cvService.updateOne(cvId, cv);

		request.setStatus(CandidateUpdateRequest.STATUS_COMPLETED);
		request.setCompletedAt(Instant.now());
		requestRepository.save(request);
		cacheEvictor.evict(tenantId);
		log.info("Candidate update completed for CV {} (tenant {})", cvId, tenantId);
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
