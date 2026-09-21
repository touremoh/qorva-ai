package ai.qorva.core.service;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.entity.CandidateOutreach;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dao.repository.CandidateOutreachRepository;
import ai.qorva.core.dao.repository.SuppressedEmailRepository;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.CandidateOutreachDTO;
import ai.qorva.core.dto.CandidateOutreachData;
import ai.qorva.core.enums.OutreachViaEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.CandidateOutreachMapper;
import ai.qorva.core.service.mailbox.MailboxConnectionService;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Recruiter → candidate outreach: the composer's context, the hand-off log, and sending through
 * the recruiter's connected mailbox. Every path re-checks the suppression list — a candidate who
 * unsubscribed through the profile-update flow asked "not to be contacted again", and that promise
 * holds whichever mailbox would send. Nothing here ever writes to {@code cvs}.
 *
 * <p>Reads CVs through the repository rather than {@code CVService} because {@code CVService}
 * calls back into this class for its delete/replace cascades (same shape as {@code NoteService}).</p>
 */
@Slf4j
@Service
public class CandidateOutreachService {

	private static final int HISTORY_SIZE = 20;
	private static final int MAX_ERROR_LENGTH = 300;

	private final CandidateOutreachRepository repository;
	private final CandidateOutreachMapper mapper;
	private final CVRepository cvRepository;
	private final SuppressedEmailRepository suppressedEmailRepository;
	private final MailboxConnectionService mailboxConnectionService;
	private final UserRepository userRepository;

	public CandidateOutreachService(CandidateOutreachRepository repository, CandidateOutreachMapper mapper,
	                                CVRepository cvRepository, SuppressedEmailRepository suppressedEmailRepository,
	                                MailboxConnectionService mailboxConnectionService, UserRepository userRepository) {
		this.repository = repository;
		this.mapper = mapper;
		this.cvRepository = cvRepository;
		this.suppressedEmailRepository = suppressedEmailRepository;
		this.mailboxConnectionService = mailboxConnectionService;
		this.userRepository = userRepository;
	}

	public CandidateOutreachData.ContextResponse context(String tenantId, String username, String cvId) throws QorvaException {
		var cv = loadCv(tenantId, cvId);
		var email = emailOf(cv);
		var mailbox = mailboxConnectionService.composerState(tenantId, username);
		return new CandidateOutreachData.ContextResponse(
			cv.getPersonalInformation() != null ? cv.getPersonalInformation().getName() : null,
			email,
			StringUtils.hasText(email) && isSuppressed(tenantId, email),
			mailbox.state(),
			mailbox.emailAddress(),
			history(tenantId, cvId));
	}

	public List<CandidateOutreachDTO> history(String tenantId, String cvId) {
		return repository.findByTenantIdAndCvIdOrderByCreatedAtDesc(tenantId, cvId, PageRequest.of(0, HISTORY_SIZE))
			.stream().map(mapper::map).toList();
	}

	/** The composer opened the recruiter's own client; record the hand-off so the team sees it. */
	public CandidateOutreachDTO recordExternal(String tenantId, String username,
	                                            CandidateOutreachData.ExternalRequest request) throws QorvaException {
		var via = OutreachViaEnum.fromHandoffValue(request.getVia());
		if (via == null) {
			throw new QorvaException(QorvaErrorCodes.OUTREACH_VIA_INVALID,
				HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
		}
		loadCv(tenantId, request.getCvId());
		assertNotSuppressed(tenantId, request.getTo());

		var saved = repository.save(row(tenantId, username, request.getCvId(), request.getJobPostId(),
			request.getMatchingReportId(), request.getTo(), request.getSubject(), request.getBody(), via)
			.status(CandidateOutreach.STATUS_EXTERNAL_OPENED)
			.build());
		log.info("Outreach handed off via {} for CV {} by {}", via, request.getCvId(), username);
		return mapper.map(saved);
	}

	/** Send as the recruiter through their connected mailbox and log the result either way. */
	public CandidateOutreachData.SendResponse send(String tenantId, String username,
	                                                CandidateOutreachData.SendRequest request) throws QorvaException {
		loadCv(tenantId, request.getCvId());
		assertNotSuppressed(tenantId, request.getTo());

		var row = row(tenantId, username, request.getCvId(), request.getJobPostId(), request.getMatchingReportId(),
			request.getTo(), request.getSubject(), request.getBody(), OutreachViaEnum.CONNECTED_MICROSOFT);
		try {
			var result = mailboxConnectionService.send(tenantId, username, request.getTo(), request.getSubject(), request.getBody());
			var saved = repository.save(row
				.status(CandidateOutreach.STATUS_SENT)
				.providerMessageId(result.providerMessageId())
				.providerThreadId(result.providerThreadId())
				.providerWebLink(result.webLink())
				.build());
			log.info("Outreach sent via connected mailbox for CV {} by {}", request.getCvId(), username);
			return new CandidateOutreachData.SendResponse(request.getTo(), saved.getCreatedAt() != null
				? saved.getCreatedAt() : Instant.now(), result.webLink(), mapper.map(saved));
		} catch (QorvaException e) {
			// A missing connection is a precondition failure, not an attempt — nothing to log as FAILED.
			if (!QorvaErrorCodes.MAILBOX_NOT_CONNECTED.equals(e.getMessage())) {
				repository.save(row.status(CandidateOutreach.STATUS_FAILED).error(abbreviate(e.getMessage())).build());
			}
			throw e;
		}
	}

	// -------------------------------------------------------------------------
	// Cascade helpers — called by the services that own the CV documents
	// -------------------------------------------------------------------------

	public long deleteForCv(String tenantId, String cvId) {
		return repository.deleteByTenantIdAndCvId(tenantId, cvId);
	}

	public long deleteForCvs(String tenantId, Collection<String> cvIds) {
		if (cvIds == null || cvIds.isEmpty()) return 0L;
		return repository.deleteByTenantIdAndCvIdIn(tenantId, cvIds);
	}

	public long deleteForTenant(String tenantId) {
		return repository.deleteByTenantId(tenantId);
	}

	/** Duplicate resolution keeps the contact trail on the CV that survives. */
	public long retarget(String tenantId, String fromCvId, String toCvId) {
		var rows = repository.findByTenantIdAndCvId(tenantId, fromCvId);
		if (rows.isEmpty()) return 0L;
		rows.forEach(r -> r.setCvId(toCvId));
		repository.saveAll(rows);
		return rows.size();
	}

	// -------------------------------------------------------------------------

	private CandidateOutreach.CandidateOutreachBuilder row(String tenantId, String username, String cvId, String jobPostId,
	                                                        String matchingReportId, String to, String subject, String body,
	                                                        OutreachViaEnum via) {
		return CandidateOutreach.builder()
			.tenantId(tenantId)
			.cvId(cvId)
			.jobPostId(StringUtils.hasText(jobPostId) ? jobPostId : null)
			.matchingReportId(StringUtils.hasText(matchingReportId) ? matchingReportId : null)
			.channel(CandidateOutreach.CHANNEL_EMAIL)
			.to(to)
			.subject(subject)
			.body(body)
			.via(via.name())
			.senderName(resolveSenderName(username));
	}

	private void assertNotSuppressed(String tenantId, String email) throws QorvaException {
		if (!StringUtils.hasText(email)) {
			throw new QorvaException(QorvaErrorCodes.OUTREACH_NO_EMAIL,
				HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
		}
		if (isSuppressed(tenantId, email)) {
			throw new QorvaException(QorvaErrorCodes.OUTREACH_SUPPRESSED,
				HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT);
		}
	}

	/** A wrong tenant and a missing document answer the same way: no existence oracle. */
	private CV loadCv(String tenantId, String cvId) throws QorvaException {
		var cv = ObjectId.isValid(cvId) ? cvRepository.findById(new ObjectId(cvId)).orElse(null) : null;
		if (cv == null || !Objects.equals(cv.getTenantId(), tenantId)) {
			throw new QorvaException(QorvaErrorCodes.HTTP_NOT_FOUND, HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND);
		}
		return cv;
	}

	/** Same rule as {@code CandidateUpdateService.isSuppressed}: the list is tenant-scoped and lower-cased. */
	private boolean isSuppressed(String tenantId, String email) {
		return suppressedEmailRepository.existsByTenantIdAndEmail(tenantId, email.trim().toLowerCase(Locale.ROOT));
	}

	private static String emailOf(CV cv) {
		var info = cv.getPersonalInformation();
		if (info == null || info.getContact() == null) return null;
		var email = info.getContact().getEmail();
		return StringUtils.hasText(email) ? email.trim() : null;
	}

	private String resolveSenderName(String email) {
		var user = userRepository.findByEmail(email);
		if (user == null) return email;
		var name = ((StringUtils.hasText(user.getFirstName()) ? user.getFirstName() : "") + " "
			+ (StringUtils.hasText(user.getLastName()) ? user.getLastName() : "")).trim();
		return StringUtils.hasText(name) ? name : email;
	}

	private static String abbreviate(String text) {
		if (text == null) return null;
		return text.length() > MAX_ERROR_LENGTH ? text.substring(0, MAX_ERROR_LENGTH) : text;
	}
}
