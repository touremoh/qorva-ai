package ai.qorva.core.service;

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

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Issues and consumes single-use, time-boxed set-password links. Used to let a freshly created
 * demo user choose their own password (no password is ever emailed), and reusable as the basis
 * for a future "forgot password" flow.
 */
@Slf4j
@Service
public class SetPasswordService {

	/** Statuses for which a set-password link may be (re)issued. */
	private static final Set<String> ELIGIBLE_STATUSES = Set.of(
		UserStatusEnum.DEMO.getValue(),
		UserStatusEnum.PENDING_SUBSCRIPTION.getValue()
	);

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
		var url = buildSetPasswordUrl(issueTokenForUser(user), lang);
		String companyName = resolveCompanyName(user.getTenantId());
		pendingEmailNotificationService.createPending(
			user.getTenantId(), user.getId(), EmailNotificationType.DEMO_WELCOME, lang,
			Map.of("setPasswordUrl", url, "companyName", companyName)
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

		int tokenVersion = claims.get(JwtUtils.CREDENTIAL_VERSION, Integer.class) != null
			? claims.get(JwtUtils.CREDENTIAL_VERSION, Integer.class) : 0;
		int currentVersion = user.getPasswordCredentialVersion() != null ? user.getPasswordCredentialVersion() : 0;
		if (tokenVersion != currentVersion) {
			throw new QorvaException(QorvaErrorCodes.AUTH_SET_PASSWORD_TOKEN_USED, HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT);
		}

		user.setEncryptedPassword(passwordEncoder.encode(newPassword));
		user.setPasswordCredentialVersion(currentVersion + 1);
		userRepository.save(user);
		log.info("Password set for userId={} (credential version {} -> {})", userId, currentVersion, currentVersion + 1);
	}

	private String issueTokenForUser(User user) {
		int version = user.getPasswordCredentialVersion() != null ? user.getPasswordCredentialVersion() : 0;
		return JwtUtils.generateSetPasswordToken(user.getId(), version, jwtConfig);
	}

	private String buildSetPasswordUrl(String token, String lang) {
		var base = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
		return base + "/" + lang + "/set-password?token=" + token;
	}

	private String resolveLang(User user) {
		return Objects.requireNonNullElse(user.getCommunicationLanguage(), "en");
	}

	private String resolveCompanyName(String tenantId) {
		try {
			var tenant = tenantService.findOneById(tenantId);
			return tenant.getTenantName() != null ? tenant.getTenantName() : "";
		} catch (Exception e) {
			log.warn("Could not resolve tenant name for tenantId={}", tenantId);
			return "";
		}
	}
}
