package ai.qorva.core.admin.service;

import ai.qorva.core.admin.dto.AdminAuthData;
import ai.qorva.core.admin.security.AdminContext;
import ai.qorva.core.admin.security.AdminTokens;
import ai.qorva.core.dao.entity.PlatformAdmin;
import ai.qorva.core.dao.repository.PlatformAdminRepository;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;

/** Admin sign-in: password, then the emailed code, then a 30-minute session that the console refreshes. */
@Slf4j
@Service
public class AdminAuthService {

	public static final int MIN_PASSWORD_LENGTH = 8;


	private final PlatformAdminRepository admins;
	private final PasswordEncoder passwordEncoder;
	private final AdminMfaService mfa;
	private final AdminTokens tokens;
	private final AdminAuditService audit;

	/** Compared against when the email is unknown, so both paths cost one BCrypt check. */
	private final String dummyHash;

	public AdminAuthService(PlatformAdminRepository admins, PasswordEncoder passwordEncoder, AdminMfaService mfa,
	                        AdminTokens tokens, AdminAuditService audit) {
		this.admins = admins;
		this.passwordEncoder = passwordEncoder;
		this.mfa = mfa;
		this.tokens = tokens;
		this.audit = audit;
		this.dummyHash = passwordEncoder.encode("not-a-password");
	}

	public AdminAuthData.Challenge login(AdminAuthData.LoginRequest request) throws QorvaException {
		var email = normalize(request.email());
		var admin = email == null ? null : admins.findByEmail(email);
		var hash = admin != null && admin.getEncryptedPassword() != null ? admin.getEncryptedPassword() : dummyHash;
		boolean matches = request.password() != null && passwordEncoder.matches(request.password(), hash);
		if (admin == null || admin.getEncryptedPassword() == null || !matches || !PlatformAdmin.STATUS_ACTIVE.equals(admin.getStatus())) {
			log.warn("Admin sign-in refused for '{}'", email);
			throw QorvaErrors.unauthorized(QorvaErrorCodes.AUTH_FAILED);
		}
		return mfa.issue(admin);
	}

	public AdminAuthData.Challenge resend(AdminAuthData.ResendRequest request) throws QorvaException {
		return mfa.resend(request.challengeId());
	}

	public AdminAuthData.Session verify(AdminAuthData.VerifyRequest request) throws QorvaException {
		var admin = mfa.verify(request.challengeId(), request.code());
		admin.setLastLoginAt(Instant.now());
		admins.save(admin);
		audit.record(admin.getId(), admin.getEmail(), "ADMIN_SIGNED_IN", null, null, "Signed in");
		return session(admin);
	}

	public AdminAuthData.Session refresh() throws QorvaException {
		return session(current());
	}

	public AdminAuthData.Admin me() throws QorvaException {
		return AdminAuthData.Admin.from(current());
	}

	/** The link from the invite email: sets the first password (or a new one), and ends any other session. */
	public void setPassword(AdminAuthData.SetPasswordRequest request) throws QorvaException {
		requireStrong(request.password());
		var claims = tokens.parse(request.token() == null ? "" : request.token(), AdminTokens.TYPE_SET_PASSWORD)
			.orElseThrow(() -> QorvaErrors.unauthorized(QorvaErrorCodes.AUTH_SET_PASSWORD_TOKEN_INVALID));
		var admin = admins.findById(claims.getSubject())
			.filter(a -> PlatformAdmin.STATUS_ACTIVE.equals(a.getStatus()))
			.orElseThrow(() -> QorvaErrors.unauthorized(QorvaErrorCodes.AUTH_SET_PASSWORD_TOKEN_INVALID));
		if (admin.getCredentialVersion() != AdminTokens.credentialVersion(claims)) {
			throw QorvaErrors.conflict(QorvaErrorCodes.AUTH_SET_PASSWORD_TOKEN_USED);
		}
		admin.setEncryptedPassword(passwordEncoder.encode(request.password()));
		admin.setCredentialVersion(admin.getCredentialVersion() + 1);
		admin.setUpdatedAt(Instant.now());
		admins.save(admin);
		audit.record(admin.getId(), admin.getEmail(), "ADMIN_PASSWORD_SET", null, null, "Password set from the emailed link");
	}

	public static void requireStrong(String password) throws QorvaException {
		if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.ADMIN_PASSWORD_TOO_SHORT, MIN_PASSWORD_LENGTH);
		}
	}

	public static String normalize(String email) {
		return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
	}

	private PlatformAdmin current() throws QorvaException {
		return admins.findById(AdminContext.current().id())
			.orElseThrow(() -> QorvaErrors.unauthorized(QorvaErrorCodes.AUTH_TOKEN_INVALID));
	}

	private AdminAuthData.Session session(PlatformAdmin admin) {
		var issued = tokens.session(admin);
		return new AdminAuthData.Session(issued.token(), issued.expiresAt(), AdminAuthData.Admin.from(admin));
	}
}
