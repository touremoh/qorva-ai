package ai.qorva.core.service;

import ai.qorva.core.security.TenantScope;

import ai.qorva.core.config.JwtConfig;
import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.enums.EmailNotificationType;
import ai.qorva.core.enums.UserStatusEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Issues and consumes single-use, time-boxed set-password links. Used to let a freshly created
 * demo user choose their own password (no password is ever emailed) and to reset a forgotten
 * password. Both flows share one token and one consume endpoint; only the TTL and the email differ.
 */
@Slf4j
@Service
public class SetPasswordService {

	/** Statuses for which an activation (set-password) link may be (re)issued. */
	private static final Set<String> ELIGIBLE_STATUSES = Set.of(
		UserStatusEnum.DEMO.getValue(),
		UserStatusEnum.PENDING_SUBSCRIPTION.getValue()
	);

	/** Statuses for which no password may be reset or set: the account must not come back to life through a link. */
	static final Set<String> BLOCKED_STATUSES = Set.of(
		UserStatusEnum.DELETED.getValue(),
		UserStatusEnum.INACTIVE.getValue(),
		UserStatusEnum.LOCKED.getValue()
	);

	/** Minimum gap between two reset emails for the same user; requests inside it are silently dropped. */
	static final Duration RESET_COOLDOWN = Duration.ofMinutes(2);

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtConfig jwtConfig;
	private final PendingEmailNotificationService pendingEmailNotificationService;
	private final TenantService tenantService;

	@Value("${weblink.appBaseUrl}")
	private String appBaseUrl;

	@Autowired
	public SetPasswordService(
		UserRepository userRepository,
		PasswordEncoder passwordEncoder,
		JwtConfig jwtConfig,
		PendingEmailNotificationService pendingEmailNotificationService,
		TenantService tenantService
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtConfig = jwtConfig;
		this.pendingEmailNotificationService = pendingEmailNotificationService;
		this.tenantService = tenantService;
	}

	/**
	 * Mints a set-password token pinned to the user's current credential version and queues a
	 * DEMO_WELCOME email containing the link. Best-effort: email failures are swallowed by the
	 * pending-email service and retried by the scheduler.
	 */
	public void enqueueDemoWelcome(String userId) {
		userRepository.findById(new ObjectId(userId)).ifPresentOrElse(
			this::enqueue,
			() -> log.warn("Cannot queue demo-welcome email – user not found for userId={}", userId)
		);
	}

	/** Re-issues a set-password link for an eligible account. Silent no-op if the email is unknown or ineligible. */
	public void resend(String email) {
		var user = userRepository.findByEmail(email);
		if (user == null) {
			log.info("Set-password resend requested for unknown email – ignoring");
			return;
		}
		if (!ELIGIBLE_STATUSES.contains(user.getUserAccountStatus())) {
			log.info("Set-password resend requested for ineligible user status={} – ignoring", user.getUserAccountStatus());
			return;
		}
		enqueue(user);
	}

	private void enqueue(User user) {
		var lang = resolveLang(user);
		var token = issueTokenForUser(user, jwtConfig.getSetPasswordTtlInMillis());
		var url = buildLinkUrl(token, lang, "set-password");
		String companyName = resolveCompanyName(user.getTenantId());
		pendingEmailNotificationService.createPending(
			user.getTenantId(), user.getId(), EmailNotificationType.DEMO_WELCOME, lang,
			Map.of("setPasswordUrl", url, "companyName", companyName)
		);
	}

	/**
	 * Forgot-password entry point: queues a PASSWORD_RESET email with a short-lived link. Silent no-op
	 * for an unknown email, a blocked account status, or a request inside {@link #RESET_COOLDOWN} —
	 * the caller always answers "success" so nothing leaks about which emails are registered.
	 */
	public void requestReset(String email) {
		var user = userRepository.findByEmail(email);
		if (user == null) {
			log.info("Password reset requested for unknown email – ignoring");
			return;
		}
		if (BLOCKED_STATUSES.contains(user.getUserAccountStatus())) {
			log.info("Password reset requested for blocked user status={} – ignoring", user.getUserAccountStatus());
			return;
		}
		if (pendingEmailNotificationService.existsRecent(user.getId(), EmailNotificationType.PASSWORD_RESET, RESET_COOLDOWN)) {
			log.info("Password reset requested inside cooldown for userId={} – ignoring", user.getId());
			return;
		}
		var lang = resolveLang(user);
		var token = issueTokenForUser(user, jwtConfig.getPasswordResetTtlInMillis());
		var url = buildLinkUrl(token, lang, "reset-password");
		pendingEmailNotificationService.createPending(
			user.getTenantId(), user.getId(), EmailNotificationType.PASSWORD_RESET, lang,
			Map.of("resetPasswordUrl", url, "companyName", resolveCompanyName(user.getTenantId()))
		);
	}

	/**
	 * Consumes a set-password token: validates purpose + single-use version, sets the new password,
	 * and bumps the credential version so the token cannot be replayed.
	 */
	public void setPassword(String token, String newPassword) throws QorvaException {
		Claims claims;
		try {
			claims = JwtUtils.extractAllClaims(token, jwtConfig.getSecretKey());
		} catch (Exception e) {
			throw new QorvaException(QorvaErrorCodes.AUTH_SET_PASSWORD_TOKEN_INVALID, HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED);
		}

		if (!JwtUtils.PURPOSE_SET_PASSWORD.equals(claims.get(JwtUtils.PURPOSE, String.class))) {
			throw new QorvaException(QorvaErrorCodes.AUTH_SET_PASSWORD_TOKEN_INVALID, HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED);
		}

		var userId = claims.getSubject();
		var user = userRepository.findById(new ObjectId(userId))
			.orElseThrow(() -> new QorvaException(QorvaErrorCodes.AUTH_SET_PASSWORD_TOKEN_INVALID, HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED));

		// The status may have changed between issue and consume; a blocked account keeps its link useless.
		if (BLOCKED_STATUSES.contains(user.getUserAccountStatus())) {
			throw new QorvaException(QorvaErrorCodes.AUTH_SET_PASSWORD_TOKEN_INVALID, HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED);
		}

		int tokenVersion = claims.get(JwtUtils.CREDENTIAL_VERSION, Integer.class) != null
			? claims.get(JwtUtils.CREDENTIAL_VERSION, Integer.class) : 0;
		int currentVersion = user.getPasswordCredentialVersionOrZero();
		if (tokenVersion != currentVersion) {
			throw new QorvaException(QorvaErrorCodes.AUTH_SET_PASSWORD_TOKEN_USED, HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT);
		}

		user.setEncryptedPassword(passwordEncoder.encode(newPassword));
		user.setPasswordCredentialVersion(currentVersion + 1);
		userRepository.save(user);
		log.info("Password set for userId={} (credential version {} -> {})", userId, currentVersion, currentVersion + 1);
	}

	private String issueTokenForUser(User user, long ttlInMillis) {
		return JwtUtils.generateSetPasswordToken(user.getId(), user.getPasswordCredentialVersionOrZero(), jwtConfig, ttlInMillis);
	}

	/** {@code path} is the app route segment (set-password / reset-password) — string-coupled with App.jsx. */
	private String buildLinkUrl(String token, String lang, String path) {
		var base = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
		return base + "/" + lang + "/" + path + "?token=" + token;
	}

	private String resolveLang(User user) {
		return Objects.requireNonNullElse(user.getCommunicationLanguage(), "en");
	}

	private String resolveCompanyName(String tenantId) {
		try {
			// Also reached from public password flows: read the user's own tenant in its scope.
			var tenant = TenantScope.callAs(tenantId, () -> tenantService.findOneById(tenantId));
			return tenant.getTenantName() != null ? tenant.getTenantName() : "";
		} catch (Exception e) {
			log.warn("Could not resolve tenant name for tenantId={}", tenantId);
			return "";
		}
	}
}
