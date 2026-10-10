package ai.qorva.core.admin.service;

import ai.qorva.core.admin.dto.AdminAuthData;
import ai.qorva.core.config.MfaProperties;
import ai.qorva.core.dao.entity.AdminMfaChallenge;
import ai.qorva.core.dao.entity.PlatformAdmin;
import ai.qorva.core.dao.repository.AdminMfaChallengeRepository;
import ai.qorva.core.dao.repository.PlatformAdminRepository;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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

/**
 * The admin sign-in code, mandatory for every admin. Same limits and the same atomic counters as the tenant
 * {@code MfaService} ({@code qorva.mfa.*}), in a collection of its own.
 */
@Slf4j
@Service
public class AdminMfaService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final AdminMfaChallengeRepository challenges;
	private final PlatformAdminRepository admins;
	private final PasswordEncoder passwordEncoder;
	private final MongoTemplate mongoTemplate;
	private final MfaProperties properties;
	private final ObjectProvider<AdminNotificationService> notifications;

	public AdminMfaService(AdminMfaChallengeRepository challenges, PlatformAdminRepository admins, PasswordEncoder passwordEncoder,
	                       MongoTemplate mongoTemplate, MfaProperties properties, ObjectProvider<AdminNotificationService> notifications) {
		this.challenges = challenges;
		this.admins = admins;
		this.passwordEncoder = passwordEncoder;
		this.mongoTemplate = mongoTemplate;
		this.properties = properties;
		this.notifications = notifications;
	}

	/** Fails closed: no email, no challenge. */
	public AdminAuthData.Challenge issue(PlatformAdmin admin) throws QorvaException {
		var now = Instant.now();
		if (challenges.countByAdminIdAndCreatedAtAfter(admin.getId(), now.minus(properties.getChallengeWindow())) >= properties.getMaxChallengesPerWindow()) {
			throw tooMany(QorvaErrorCodes.AUTH_MFA_TOO_MANY_CODES);
		}
		var code = newCode();
		var challenge = challenges.save(AdminMfaChallenge.builder()
			.id(newChallengeId())
			.adminId(admin.getId())
			.codeHash(passwordEncoder.encode(code))
			.sends(1)
			.lastSentAt(now)
			.expiresAt(now.plus(properties.getCodeTtl()))
			.createdAt(now)
			.build());
		try {
			deliver(admin, code);
		} catch (QorvaException e) {
			challenges.deleteById(challenge.getId());
			throw e;
		}
		return view(challenge, admin);
	}

	public AdminAuthData.Challenge resend(String challengeId) throws QorvaException {
		var challenge = loadOpen(challengeId);
		var admin = loadAdmin(challenge);
		var now = Instant.now();
		if (challenge.getSends() >= properties.getMaxSendsPerChallenge()) {
			throw tooMany(QorvaErrorCodes.AUTH_MFA_TOO_MANY_CODES);
		}
		if (challenge.getLastSentAt() != null && now.isBefore(challenge.getLastSentAt().plus(properties.getResendCooldown()))) {
			throw tooMany(QorvaErrorCodes.AUTH_MFA_RESEND_TOO_SOON);
		}
		var code = newCode();
		var updated = mongoTemplate.findAndModify(
			byId(challengeId).addCriteria(Criteria.where("consumedAt").is(null)).addCriteria(Criteria.where("sends").is(challenge.getSends())),
			new Update().set("codeHash", passwordEncoder.encode(code)).set("lastSentAt", now).inc("sends", 1),
			FindAndModifyOptions.options().returnNew(true), AdminMfaChallenge.class);
		if (updated == null) {
			throw tooMany(QorvaErrorCodes.AUTH_MFA_RESEND_TOO_SOON);
		}
		deliver(admin, code);
		return view(updated, admin);
	}

	/** The attempt is reserved before the comparison, so parallel guesses cannot exceed the limit. */
	public PlatformAdmin verify(String challengeId, String code) throws QorvaException {
		var challenge = loadOpen(challengeId);
		var admin = loadAdmin(challenge);
		var reserved = mongoTemplate.findAndModify(
			byId(challengeId).addCriteria(Criteria.where("consumedAt").is(null)).addCriteria(Criteria.where("attempts").lt(properties.getMaxAttempts())),
			new Update().inc("attempts", 1), FindAndModifyOptions.options().returnNew(true), AdminMfaChallenge.class);
		if (reserved == null) {
			throw invalid();
		}
		var normalized = code == null ? "" : code.replaceAll("\\s", "");
		if (normalized.matches("\\d{" + properties.getCodeLength() + "}") && passwordEncoder.matches(normalized, reserved.getCodeHash())) {
			var consumed = mongoTemplate.updateFirst(byId(challengeId).addCriteria(Criteria.where("consumedAt").is(null)),
				new Update().set("consumedAt", Instant.now()), AdminMfaChallenge.class);
			if (consumed.getModifiedCount() == 0) {
				throw invalid();
			}
			return admin;
		}
		int remaining = properties.getMaxAttempts() - reserved.getAttempts();
		if (remaining <= 0) {
			mongoTemplate.updateFirst(byId(challengeId), new Update().set("consumedAt", Instant.now()), AdminMfaChallenge.class);
			log.warn("Admin MFA challenge burnt after {} wrong codes: adminId={}", reserved.getAttempts(), admin.getId());
			throw tooMany(QorvaErrorCodes.AUTH_MFA_TOO_MANY_ATTEMPTS);
		}
		throw new QorvaException(QorvaErrorCodes.AUTH_MFA_CODE_INVALID, HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED, remaining);
	}

	private AdminMfaChallenge loadOpen(String challengeId) throws QorvaException {
		if (challengeId == null || challengeId.isBlank()) {
			throw invalid();
		}
		var challenge = challenges.findById(challengeId).orElseThrow(AdminMfaService::invalid);
		if (challenge.getConsumedAt() != null || challenge.getExpiresAt() == null || !Instant.now().isBefore(challenge.getExpiresAt())) {
			throw invalid();
		}
		return challenge;
	}

	private PlatformAdmin loadAdmin(AdminMfaChallenge challenge) throws QorvaException {
		return admins.findById(challenge.getAdminId())
			.filter(a -> PlatformAdmin.STATUS_ACTIVE.equals(a.getStatus()))
			.orElseThrow(AdminMfaService::invalid);
	}

	private void deliver(PlatformAdmin admin, String code) throws QorvaException {
		var notifier = notifications.getIfAvailable();
		if (notifier == null) {
			log.error("Admin MFA code not sent: notifications are disabled, adminId={}", admin.getId());
			throw QorvaErrors.of(QorvaErrorCodes.AUTH_MFA_DELIVERY_FAILED, HttpStatus.SERVICE_UNAVAILABLE);
		}
		try {
			notifier.sendCode(admin, code, properties.getCodeTtl().toMinutes());
		} catch (QorvaException e) {
			throw QorvaErrors.of(QorvaErrorCodes.AUTH_MFA_DELIVERY_FAILED, HttpStatus.SERVICE_UNAVAILABLE);
		}
	}

	private AdminAuthData.Challenge view(AdminMfaChallenge c, PlatformAdmin admin) {
		var resendAt = c.getLastSentAt() != null ? c.getLastSentAt().plus(properties.getResendCooldown()) : Instant.now();
		return new AdminAuthData.Challenge(c.getId(), mask(admin.getEmail()), c.getExpiresAt(), resendAt);
	}

	private String newCode() {
		int bound = (int) Math.pow(10, properties.getCodeLength());
		return String.format("%0" + properties.getCodeLength() + "d", RANDOM.nextInt(bound));
	}

	private static String newChallengeId() {
		var bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String mask(String email) {
		int at = email == null ? -1 : email.indexOf('@');
		return at <= 0 ? "•••" : email.charAt(0) + "•••" + email.substring(at);
	}

	private static Query byId(String id) {
		return new Query(Criteria.where("_id").is(id));
	}

	private static QorvaException invalid() {
		return QorvaErrors.unauthorized(QorvaErrorCodes.AUTH_MFA_CHALLENGE_INVALID);
	}

	private static QorvaException tooMany(String code) {
		return QorvaErrors.of(code, HttpStatus.TOO_MANY_REQUESTS);
	}
}
