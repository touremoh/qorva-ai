package ai.qorva.core.admin.service;

import ai.qorva.core.admin.config.AdminProperties;
import ai.qorva.core.admin.dto.AdminAuthData;
import ai.qorva.core.admin.security.AdminContext;
import ai.qorva.core.admin.security.AdminTokens;
import ai.qorva.core.dao.entity.PlatformAdmin;
import ai.qorva.core.dao.repository.PlatformAdminRepository;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** The admins themselves: invite, name, role, enable/disable. OWNER only (enforced on the controller), except {@link #updateProfile}. */
@Slf4j
@Service
public class AdminAccountService {

	private static final Set<String> ROLES = Set.of(PlatformAdmin.ROLE_OWNER, PlatformAdmin.ROLE_SUPPORT);
	private static final Set<String> STATUSES = Set.of(PlatformAdmin.STATUS_ACTIVE, PlatformAdmin.STATUS_DISABLED);
	static final int NAME_MAX_LENGTH = 100;

	private final PlatformAdminRepository admins;
	private final AdminTokens tokens;
	private final AdminProperties properties;
	private final ObjectProvider<AdminNotificationService> notifications;
	private final AdminAuditService audit;

	public AdminAccountService(PlatformAdminRepository admins, AdminTokens tokens, AdminProperties properties,
	                           ObjectProvider<AdminNotificationService> notifications, AdminAuditService audit) {
		this.admins = admins;
		this.tokens = tokens;
		this.properties = properties;
		this.notifications = notifications;
		this.audit = audit;
	}

	public List<AdminAuthData.Admin> list() {
		return admins.findAllByOrderByCreatedAtAsc().stream().map(AdminAuthData.Admin::from).toList();
	}

	public AdminAuthData.Admin create(AdminAuthData.CreateAdminRequest request) throws QorvaException {
		var email = AdminAuthService.normalize(request.email());
		if (email == null || !email.contains("@")) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.HTTP_VALIDATION);
		}
		requireRole(request.role());
		if (admins.findByEmail(email) != null) {
			throw QorvaErrors.conflict(QorvaErrorCodes.ADMIN_ADMIN_EXISTS);
		}
		var admin = invite(email, name(request.firstName()), name(request.lastName()), request.role(), AdminContext.actor());
		audit.record("ADMIN_CREATED", null, null, "Admin " + email + " invited as " + request.role());
		return AdminAuthData.Admin.from(admin);
	}

	public AdminAuthData.Admin update(String id, AdminAuthData.UpdateAdminRequest request) throws QorvaException {
		var admin = require(id);
		var me = AdminContext.current();
		var role = request.role() != null ? request.role() : admin.getRole();
		var status = request.status() != null ? request.status() : admin.getStatus();
		var firstName = request.firstName() != null ? name(request.firstName()) : admin.getFirstName();
		var lastName = request.lastName() != null ? name(request.lastName()) : admin.getLastName();
		requireRole(role);
		if (!STATUSES.contains(status)) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.ADMIN_INVALID_STATUS, status);
		}
		if (admin.getId().equals(me.id()) && !PlatformAdmin.STATUS_ACTIVE.equals(status)) {
			throw QorvaErrors.conflict(QorvaErrorCodes.ADMIN_LAST_OWNER);
		}
		boolean losesOwner = isActiveOwner(admin) && !(PlatformAdmin.ROLE_OWNER.equals(role) && PlatformAdmin.STATUS_ACTIVE.equals(status));
		if (losesOwner && admins.countByRoleAndStatus(PlatformAdmin.ROLE_OWNER, PlatformAdmin.STATUS_ACTIVE) <= 1) {
			throw QorvaErrors.conflict(QorvaErrorCodes.ADMIN_LAST_OWNER);
		}
		boolean accessChanges = !Objects.equals(role, admin.getRole()) || !Objects.equals(status, admin.getStatus());
		boolean nameChanges = !Objects.equals(firstName, admin.getFirstName()) || !Objects.equals(lastName, admin.getLastName());
		if (!accessChanges && !nameChanges) {
			return AdminAuthData.Admin.from(admin);
		}
		if (accessChanges) {
			var summary = "Admin " + admin.getEmail() + ": " + admin.getRole() + "/" + admin.getStatus() + " → " + role + "/" + status;
			admin.setRole(role);
			admin.setStatus(status);
			// A new role or status ends the admin's sessions; a new name does not.
			admin.setCredentialVersion(admin.getCredentialVersion() + 1);
			audit.record("ADMIN_UPDATED", null, null, summary);
		}
		if (nameChanges) {
			rename(admin, firstName, lastName);
		}
		admin.setUpdatedAt(Instant.now());
		admins.save(admin);
		return AdminAuthData.Admin.from(admin);
	}

	/** Any signed-in admin, SUPPORT included, may change their own name. Sessions stay open. */
	public AdminAuthData.Admin updateProfile(AdminAuthData.UpdateProfileRequest request) throws QorvaException {
		var admin = require(AdminContext.current().id());
		var firstName = request.firstName() != null ? name(request.firstName()) : admin.getFirstName();
		var lastName = request.lastName() != null ? name(request.lastName()) : admin.getLastName();
		if (Objects.equals(firstName, admin.getFirstName()) && Objects.equals(lastName, admin.getLastName())) {
			return AdminAuthData.Admin.from(admin);
		}
		rename(admin, firstName, lastName);
		admin.setUpdatedAt(Instant.now());
		admins.save(admin);
		return AdminAuthData.Admin.from(admin);
	}

	private void rename(PlatformAdmin admin, String firstName, String lastName) {
		audit.record("ADMIN_RENAMED", null, null, "Admin " + admin.getEmail() + " renamed to " + fullName(firstName, lastName));
		admin.setFirstName(firstName);
		admin.setLastName(lastName);
	}

	private static String fullName(String firstName, String lastName) {
		var full = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
		return full.isEmpty() ? "(no name)" : full;
	}

	/** Trimmed; blank → null (no name). */
	private static String name(String value) throws QorvaException {
		if (value == null || value.isBlank()) {
			return null;
		}
		var trimmed = value.trim();
		if (trimmed.length() > NAME_MAX_LENGTH) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.ADMIN_NAME_TOO_LONG, NAME_MAX_LENGTH);
		}
		return trimmed;
	}

	/** A fresh link; the previous one stops working. */
	public void resendInvite(String id) throws QorvaException {
		var admin = require(id);
		admin.setCredentialVersion(admin.getCredentialVersion() + 1);
		admin.setInvitedAt(Instant.now());
		admins.save(admin);
		sendInvite(admin);
		audit.record("ADMIN_INVITE_RESENT", null, null, "Invite re-sent to " + admin.getEmail());
	}

	/** Creates the admin without a password and emails the set-password link. Also used by the bootstrap. */
	PlatformAdmin invite(String email, String firstName, String lastName, String role, String invitedBy) throws QorvaException {
		var now = Instant.now();
		var admin = admins.save(PlatformAdmin.builder()
			.email(email)
			.firstName(firstName)
			.lastName(lastName)
			.role(role)
			.status(PlatformAdmin.STATUS_ACTIVE)
			.credentialVersion(0)
			.invitedAt(now)
			.invitedBy(invitedBy)
			.createdAt(now)
			.updatedAt(now)
			.build());
		try {
			sendInvite(admin);
		} catch (QorvaException | RuntimeException e) {
			// No link, no admin: otherwise an invite that never left would leave an account nobody can activate.
			admins.delete(admin);
			throw e;
		}
		return admin;
	}

	private void sendInvite(PlatformAdmin admin) throws QorvaException {
		var notifier = notifications.getIfAvailable();
		if (notifier == null) {
			log.error("Admin invite for {} not sent: notifications are disabled", admin.getEmail());
			throw QorvaErrors.of(QorvaErrorCodes.AUTH_MFA_DELIVERY_FAILED, HttpStatus.SERVICE_UNAVAILABLE);
		}
		var link = properties.getConsoleBaseUrl().replaceAll("/+$", "") + "/set-password?token=" + tokens.setPasswordLink(admin);
		notifier.sendInvite(admin, link, properties.getSetPasswordTtl().toHours());
	}

	private PlatformAdmin require(String id) throws QorvaException {
		return admins.findById(id).orElseThrow(() -> QorvaErrors.notFound(QorvaErrorCodes.ADMIN_ADMIN_NOT_FOUND));
	}

	private static boolean isActiveOwner(PlatformAdmin admin) {
		return PlatformAdmin.ROLE_OWNER.equals(admin.getRole()) && PlatformAdmin.STATUS_ACTIVE.equals(admin.getStatus());
	}

	private static void requireRole(String role) throws QorvaException {
		if (role == null || !ROLES.contains(role)) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.ADMIN_INVALID_ROLE, role);
		}
	}
}
