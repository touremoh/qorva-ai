package ai.qorva.core.service;

import ai.qorva.core.exception.QorvaErrors;

import ai.qorva.core.config.MfaProperties;
import ai.qorva.core.dao.entity.MfaChallenge;
import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dao.repository.MfaChallengeRepository;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.MfaData;
import ai.qorva.core.enums.MfaPurpose;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;

/**
 * Email MFA: issues, re-sends and verifies one-time codes, and switches a user's MFA on or off
 * once they have proved they receive them. Codes are stored only as BCrypt hashes; every counter
 * is updated atomically in Mongo so limits hold across App Runner instances.
 */
@Slf4j
@Service
public class MfaService {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Base64.Encoder CHALLENGE_ID_ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Set<MfaPurpose> LOGIN_ONLY = Set.of(MfaPurpose.LOGIN);
	private static final Set<MfaPurpose> SETTINGS_PURPOSES = Set.of(MfaPurpose.ENABLE, MfaPurpose.DISABLE);

	private final MfaChallengeRepository challengeRepository;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final MongoTemplate mongoTemplate;
	private final MfaProperties properties;
	private final ObjectProvider<MfaNotificationService> notificationService;

	@Autowired
	public MfaService(
		MfaChallengeRepository challengeRepository,
		UserRepository userRepository,
		PasswordEncoder passwordEncoder,
		MongoTemplate mongoTemplate,
		MfaProperties properties,
		ObjectProvider<MfaNotificationService> notificationService
	) {
		this.challengeRepository = challengeRepository;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.mongoTemplate = mongoTemplate;
		this.properties = properties;
		this.notificationService = notificationService;
	}

	// -------------------------------------------------------------------------
	// Sign-in
	// -------------------------------------------------------------------------

	/** Opens a LOGIN challenge for a user whose password was just accepted, and emails the code. */
	public MfaData.Challenge issueLogin(User user) throws QorvaException {
		return issue(user, MfaPurpose.LOGIN);
	}

	public MfaData.Challenge resendLogin(String challengeId) throws QorvaException {
		return resend(challengeId, LOGIN_ONLY, null);
	}

	/** Consumes a LOGIN challenge; returns the user to sign in. */
	public User verifyLogin(String challengeId, String code) throws QorvaException {
		return verify(challengeId, code, LOGIN_ONLY, null);
	}

	// -------------------------------------------------------------------------
	// Settings (the signed-in user's own account)
	// -------------------------------------------------------------------------

	public MfaData.Status status(String tenantId, String email) throws QorvaException {
		var user = currentUser(tenantId, email);
		return new MfaData.Status(user.isMfaEnabledOrFalse(), user.getEmail());
	}

	/** Emails a code that, once confirmed, turns MFA on ({@code enable}) or off. */
	public MfaData.Challenge startChange(String tenantId, String email, boolean enable) throws QorvaException {
		var user = currentUser(tenantId, email);
		assertCanChange(user, enable);
		return issue(user, enable ? MfaPurpose.ENABLE : MfaPurpose.DISABLE);
	}

	public MfaData.Challenge resendChange(String tenantId, String email, String challengeId) throws QorvaException {
		var user = currentUser(tenantId, email);
		return resend(challengeId, SETTINGS_PURPOSES, user.getId());
	}

	public MfaData.Status confirmChange(String tenantId, String email, boolean enable, String challengeId, String code) throws QorvaException {
		var user = currentUser(tenantId, email);
		assertCanChange(user, enable);
		verify(challengeId, code, Set.of(enable ? MfaPurpose.ENABLE : MfaPurpose.DISABLE), user.getId());

		user.setMfaEnabled(enable);
		user.setMfaEnabledAt(enable ? Instant.now() : null);
		userRepository.save(user);
		log.info("MFA {} for userId={}", enable ? "enabled" : "disabled", user.getId());
		return new MfaData.Status(enable, user.getEmail());
	}

	// -------------------------------------------------------------------------
	// Challenge lifecycle
	// -------------------------------------------------------------------------

	/** Fails closed: when the email cannot be sent, the challenge is deleted and nobody gets in. */
	MfaData.Challenge issue(User user, MfaPurpose purpose) throws QorvaException {
		var now = Instant.now();
		long recent = challengeRepository.countByUserIdAndCreatedAtAfter(user.getId(), now.minus(properties.getChallengeWindow()));
		if (recent >= properties.getMaxChallengesPerWindow()) {
			log.warn("MFA challenge refused, {} opened in the window: userId={}", recent, user.getId());
			throw tooManyRequests(QorvaErrorCodes.AUTH_MFA_TOO_MANY_CODES);
		}

		var code = newCode();
		var challenge = MfaChallenge.builder()
			.id(newChallengeId())
			.tenantId(user.getTenantId())
			.userId(user.getId())
			.purpose(purpose.name())
			.codeHash(passwordEncoder.encode(code))
			.attempts(0)
			.sends(1)
			.lastSentAt(now)
			.expiresAt(now.plus(properties.getCodeTtl()))
			.createdAt(now)
			.build();
		challengeRepository.save(challenge);

		try {
			deliver(user, code, purpose);
		} catch (QorvaException e) {
			challengeRepository.deleteById(challenge.getId());
			throw e;
		}
		log.info("MFA challenge issued: userId={} purpose={}", user.getId(), purpose);
		return view(challenge, user);
	}

	/** Replaces the code on an open challenge (the previous one stops working) and emails it. */
	MfaData.Challenge resend(String challengeId, Set<MfaPurpose> purposes, String expectedUserId) throws QorvaException {
		var challenge = loadOpen(challengeId, purposes, expectedUserId);
		var user = loadUser(challenge);
		var now = Instant.now();

		if (challenge.getSends() >= properties.getMaxSendsPerChallenge()) {
			throw tooManyRequests(QorvaErrorCodes.AUTH_MFA_TOO_MANY_CODES);
		}
		var previousSentAt = challenge.getLastSentAt();
		if (previousSentAt != null && now.isBefore(previousSentAt.plus(properties.getResendCooldown()))) {
			throw tooManyRequests(QorvaErrorCodes.AUTH_MFA_RESEND_TOO_SOON);
		}

		var code = newCode();
		// Conditional on the snapshot's send count so two concurrent resends cannot both pass the checks above.
		var guard = byId(challengeId)
			.addCriteria(Criteria.where("consumedAt").is(null))
			.addCriteria(Criteria.where("sends").is(challenge.getSends()));
		var updated = mongoTemplate.findAndModify(guard,
			new Update().set("codeHash", passwordEncoder.encode(code)).set("lastSentAt", now).inc("sends", 1),
			FindAndModifyOptions.options().returnNew(true), MfaChallenge.class);
		if (updated == null) {
			throw tooManyRequests(QorvaErrorCodes.AUTH_MFA_RESEND_TOO_SOON);
		}

		try {
			deliver(user, code, MfaPurpose.valueOf(challenge.getPurpose()));
		} catch (QorvaException e) {
			// Let the user retry straight away instead of waiting out a cooldown for an email that never left.
			mongoTemplate.updateFirst(byId(challengeId), new Update().set("lastSentAt", previousSentAt), MfaChallenge.class);
			throw e;
		}
		log.info("MFA code re-sent: userId={} purpose={} sends={}", user.getId(), challenge.getPurpose(), updated.getSends());
		return view(updated, user);
	}

	/**
	 * Checks a code. An attempt is reserved atomically <em>before</em> the comparison, so parallel
	 * guesses cannot get past {@code maxAttempts}; the last allowed wrong guess burns the challenge.
	 */
	User verify(String challengeId, String code, Set<MfaPurpose> purposes, String expectedUserId) throws QorvaException {
		var challenge = loadOpen(challengeId, purposes, expectedUserId);
		var user = loadUser(challenge);

		var reserved = mongoTemplate.findAndModify(
			byId(challengeId)
				.addCriteria(Criteria.where("consumedAt").is(null))
				.addCriteria(Criteria.where("attempts").lt(properties.getMaxAttempts())),
			new Update().inc("attempts", 1),
			FindAndModifyOptions.options().returnNew(true), MfaChallenge.class);
		if (reserved == null) {
			throw invalidChallenge();
		}

		var normalized = code == null ? "" : code.replaceAll("\\s", "");
		boolean matches = normalized.matches("\\d{" + properties.getCodeLength() + "}")
			&& passwordEncoder.matches(normalized, reserved.getCodeHash());

		if (matches) {
			var consumed = mongoTemplate.updateFirst(
				byId(challengeId).addCriteria(Criteria.where("consumedAt").is(null)),
				new Update().set("consumedAt", Instant.now()), MfaChallenge.class);
			if (consumed.getModifiedCount() == 0) {
				throw invalidChallenge();
			}
			log.info("MFA challenge verified: userId={} purpose={}", user.getId(), challenge.getPurpose());
			return user;
		}

		int remaining = properties.getMaxAttempts() - reserved.getAttempts();
		if (remaining <= 0) {
			mongoTemplate.updateFirst(byId(challengeId), new Update().set("consumedAt", Instant.now()), MfaChallenge.class);
			log.warn("MFA challenge burnt after {} wrong codes: userId={} purpose={}",
				reserved.getAttempts(), user.getId(), challenge.getPurpose());
			throw tooManyRequests(QorvaErrorCodes.AUTH_MFA_TOO_MANY_ATTEMPTS);
		}
		throw new QorvaException(QorvaErrorCodes.AUTH_MFA_CODE_INVALID,
			HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED, remaining);
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private MfaChallenge loadOpen(String challengeId, Set<MfaPurpose> purposes, String expectedUserId) throws QorvaException {
		if (challengeId == null || challengeId.isBlank()) {
			throw invalidChallenge();
		}
		var challenge = challengeRepository.findById(challengeId).orElseThrow(MfaService::invalidChallenge);
		boolean open = challenge.getConsumedAt() == null
			&& challenge.getExpiresAt() != null
			&& Instant.now().isBefore(challenge.getExpiresAt());
		boolean purposeOk = purposes.stream().anyMatch(p -> p.name().equals(challenge.getPurpose()));
		boolean ownerOk = expectedUserId == null || expectedUserId.equals(challenge.getUserId());
		if (!open || !purposeOk || !ownerOk) {
			throw invalidChallenge();
		}
		return challenge;
	}

	/** The account may have been blocked since the code was sent; a blocked account keeps its challenge useless. */
	private User loadUser(MfaChallenge challenge) throws QorvaException {
		var user = userRepository.findById(new ObjectId(challenge.getUserId())).orElseThrow(MfaService::invalidChallenge);
		if (SetPasswordService.BLOCKED_STATUSES.contains(user.getUserAccountStatus())) {
			throw invalidChallenge();
		}
		return user;
	}

	private User currentUser(String tenantId, String email) throws QorvaException {
		var user = userRepository.findByEmail(email);
		if (user == null || tenantId == null || !tenantId.equals(user.getTenantId())) {
			throw QorvaErrors.notFound(QorvaErrorCodes.USER_NOT_FOUND);
		}
		return user;
	}

	private static void assertCanChange(User user, boolean enable) throws QorvaException {
		if (enable && user.isMfaEnabledOrFalse()) {
			throw QorvaErrors.conflict(QorvaErrorCodes.MFA_ALREADY_ENABLED);
		}
		if (!enable && !user.isMfaEnabledOrFalse()) {
			throw QorvaErrors.conflict(QorvaErrorCodes.MFA_ALREADY_DISABLED);
		}
	}

	private void deliver(User user, String code, MfaPurpose purpose) throws QorvaException {
		var notifier = notificationService.getIfAvailable();
		if (notifier == null) {
			log.error("MFA code not sent: notifications are disabled (qorva.notifications.enabled=false), userId={}", user.getId());
			throw deliveryFailed();
		}
		try {
			notifier.sendCode(user, code, purpose, properties.getCodeTtl().toMinutes());
		} catch (QorvaException e) {
			throw deliveryFailed();
		}
	}

	private MfaData.Challenge view(MfaChallenge challenge, User user) {
		var resendAt = challenge.getLastSentAt() != null
			? challenge.getLastSentAt().plus(properties.getResendCooldown())
			: Instant.now();
		return new MfaData.Challenge(challenge.getId(), maskEmail(user.getEmail()), challenge.getExpiresAt(), resendAt);
	}

	private String newCode() {
		int bound = (int) Math.pow(10, properties.getCodeLength());
		return String.format("%0" + properties.getCodeLength() + "d", RANDOM.nextInt(bound));
	}

	private static String newChallengeId() {
		var bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return CHALLENGE_ID_ENCODER.encodeToString(bytes);
	}

	/** {@code jane.doe@acme.com} → {@code j•••@acme.com}. */
	static String maskEmail(String email) {
		if (email == null) {
			return "";
		}
		int at = email.indexOf('@');
		if (at <= 0) {
			return "•••";
		}
		return email.charAt(0) + "•••" + email.substring(at);
	}

	private static Query byId(String challengeId) {
		return new Query(Criteria.where("_id").is(challengeId));
	}

	private static QorvaException invalidChallenge() {
		return QorvaErrors.unauthorized(QorvaErrorCodes.AUTH_MFA_CHALLENGE_INVALID);
	}

	private static QorvaException tooManyRequests(String errorCode) {
		return new QorvaException(errorCode, HttpStatus.TOO_MANY_REQUESTS.value(), HttpStatus.TOO_MANY_REQUESTS);
	}

	private static QorvaException deliveryFailed() {
		return new QorvaException(QorvaErrorCodes.AUTH_MFA_DELIVERY_FAILED,
			HttpStatus.SERVICE_UNAVAILABLE.value(), HttpStatus.SERVICE_UNAVAILABLE);
	}
}
