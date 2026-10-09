package ai.qorva.core.service;

import ai.qorva.core.dao.entity.CandidateUpdateRequest;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Sends one candidate a profile-update request (tokenized link + invitation email), with the checks every sender
 * shares: an email on file, not unsubscribed, no request in progress, and none answered or expired within the
 * cooldown. Used by Data Health's campaign and by Copilot's request_profile_update.
 */
@Slf4j
@Component
public class CandidateUpdateRequestSender {

	public enum Outcome { SENT, SKIPPED_NO_EMAIL, SKIPPED_SUPPRESSED, SKIPPED_ACTIVE, SKIPPED_RECENT }

	private final CandidateUpdateService candidateUpdateService;
	private final CandidateUpdateEmailService emailService;
	private final MongoTemplate mongoTemplate;
	private final Duration cooldown;

	public CandidateUpdateRequestSender(CandidateUpdateService candidateUpdateService, CandidateUpdateEmailService emailService,
	                                    MongoTemplate mongoTemplate,
	                                    @Value("${qorva.candidate-update.cooldown-days:90}") int cooldownDays) {
		this.candidateUpdateService = candidateUpdateService;
		this.emailService = emailService;
		this.mongoTemplate = mongoTemplate;
		this.cooldown = Duration.ofDays(cooldownDays);
	}

	/** What {@link #send} would do for this CV, without sending anything. */
	public Outcome check(String tenantId, CVDTO cv) {
		var email = email(cv);
		if (!StringUtils.hasText(email)) return Outcome.SKIPPED_NO_EMAIL;
		if (candidateUpdateService.isSuppressed(tenantId, email)) return Outcome.SKIPPED_SUPPRESSED;
		if (candidateUpdateService.hasActiveRequest(tenantId, cv.getId())) return Outcome.SKIPPED_ACTIVE;
		if (hasRecentRequest(tenantId, cv.getId())) return Outcome.SKIPPED_RECENT;
		return Outcome.SENT;
	}

	/** Sends the request when {@link #check} allows it; returns what happened. */
	public Outcome send(String tenantId, CVDTO cv, String tenantName, String language,
	                    CandidateUpdateEmailService.CustomTemplate template, String senderName) throws QorvaException {
		var outcome = check(tenantId, cv);
		if (outcome != Outcome.SENT) return outcome;
		var email = email(cv);
		var token = candidateUpdateService.createRequest(tenantId, cv.getId(), email, language);
		emailService.sendUpdateInvitation(email, cv.getPersonalInformation().getName(), tenantName,
			candidateUpdateService.buildUpdateLink(token), language, template, senderName);
		return Outcome.SENT;
	}

	/** A request answered or left to expire within the cooldown: the candidate is not asked again so soon. */
	boolean hasRecentRequest(String tenantId, String cvId) {
		var query = Query.query(Criteria.where("tenantId").is(new ObjectId(tenantId)).and("cvId").is(cvId)
			.and("status").in(List.of(CandidateUpdateRequest.STATUS_COMPLETED, CandidateUpdateRequest.STATUS_EXPIRED))
			.and("sentAt").gte(Instant.now().minus(cooldown)));
		return mongoTemplate.exists(query, CandidateUpdateRequest.class);
	}

	private static String email(CVDTO cv) {
		var info = cv.getPersonalInformation();
		return info != null && info.getContact() != null ? info.getContact().getEmail() : null;
	}
}
