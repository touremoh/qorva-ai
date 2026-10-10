package ai.qorva.core.admin.service;

import ai.qorva.core.admin.dto.AdminUserData;
import ai.qorva.core.admin.security.AdminContext;
import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dto.common.UserAuthority;
import ai.qorva.core.dto.request.AddUserRequest;
import ai.qorva.core.enums.UserActionsEnum;
import ai.qorva.core.enums.UserPermissionEnum;
import ai.qorva.core.enums.UserRoleEnum;
import ai.qorva.core.enums.UserStatusEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantScope;
import ai.qorva.core.service.UserService;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The users of one company, from the admin console. Status changes end the user's sessions at once; nothing may leave
 * a company without a usable user who can manage users.
 */
@Service
public class AdminTenantUserService {

	private static final Set<String> SETTABLE = Set.of(UserStatusEnum.ACTIVE.getValue(), UserStatusEnum.INACTIVE.getValue(),
		UserStatusEnum.LOCKED.getValue());
	private static final Set<String> UNUSABLE = Set.of(UserStatusEnum.INACTIVE.getValue(), UserStatusEnum.LOCKED.getValue(),
		UserStatusEnum.DELETED.getValue());

	/** Invited without a choice: everything but billing, user management and integrations. */
	static final List<String> DEFAULT_INVITE_ACTIONS = Arrays.stream(UserActionsEnum.values()).map(UserActionsEnum::getValue)
		.filter(a -> !Set.of("UPDATE_SUBSCRIPTION", "CANCEL_SUBSCRIPTION", "MANAGE_USERS", "MANAGE_INTEGRATIONS").contains(a))
		.toList();

	private final AdminTenantSupport support;
	private final AdminAuditService audit;
	private final UserService userService;

	public AdminTenantUserService(AdminTenantSupport support, AdminAuditService audit, UserService userService) {
		this.support = support;
		this.audit = audit;
		this.userService = userService;
	}

	public List<AdminUserData.TenantUser> list(String tenantId) throws QorvaException {
		support.requireTenant(tenantId);
		var users = support.usersOf(tenantId);
		var ownerId = users.isEmpty() ? null : users.getFirst().getId();
		return users.stream().map(u -> view(u, u.getId().equals(ownerId))).toList();
	}

	public AdminUserData.TenantUser invite(String tenantId, AdminUserData.InviteRequest request) throws QorvaException {
		support.requireTenant(tenantId);
		var email = AdminAuthService.normalize(request.email());
		if (email == null || !email.contains("@") || !StringUtils.hasText(request.firstName()) || !StringUtils.hasText(request.lastName())) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.HTTP_VALIDATION);
		}
		AdminTenantService.assertEmailFree(support, email);
		var actions = request.authorities() == null || request.authorities().isEmpty() ? DEFAULT_INVITE_ACTIONS : request.authorities();
		var add = new AddUserRequest();
		add.setFirstName(request.firstName().trim());
		add.setLastName(request.lastName().trim());
		add.setEmail(email);
		add.setCommunicationLanguage(StringUtils.hasText(request.language()) ? request.language() : "en");
		add.setAuthorities(authorities(actions, UserRoleEnum.ACCOUNT_MANAGER));
		var created = TenantScope.callAs(tenantId, () -> userService.addUser(tenantId, add, AdminContext.actor()));
		audit.record("USER_INVITED", tenantId, created.getId(), email + " invited with " + actions.size() + " permission(s)");
		return get(tenantId, created.getId());
	}

	public AdminUserData.TenantUser rename(String tenantId, String userId, AdminUserData.UpdateRequest request) throws QorvaException {
		var user = support.requireUser(tenantId, userId);
		var update = new Update();
		if (StringUtils.hasText(request.firstName())) update.set("firstName", request.firstName().trim());
		if (StringUtils.hasText(request.lastName())) update.set("lastName", request.lastName().trim());
		if (!update.getUpdateObject().isEmpty()) {
			updateUser(userId, update);
			audit.record("USER_RENAMED", tenantId, userId, user.getFirstName() + " " + user.getLastName() + " → "
				+ Objects.requireNonNullElse(request.firstName(), user.getFirstName()) + " " + Objects.requireNonNullElse(request.lastName(), user.getLastName()));
		}
		return get(tenantId, userId);
	}

	/** Replaces the whole list of allowed actions; refused if it would leave nobody able to manage users. */
	public AdminUserData.TenantUser setAuthorities(String tenantId, String userId, List<String> actions) throws QorvaException {
		var user = support.requireUser(tenantId, userId);
		var requested = actions == null ? List.<String>of() : actions;
		if (!requested.contains(UserActionsEnum.MANAGE_USERS.getValue())) {
			assertAnotherManager(tenantId, user);
		}
		var role = isOwner(tenantId, userId) ? UserRoleEnum.ACCOUNT_OWNER : UserRoleEnum.ACCOUNT_MANAGER;
		updateUser(userId, new Update().set("authorities", authorities(requested, role)));
		audit.record("USER_AUTHORITIES_CHANGED", tenantId, userId, allowed(user) + " → " + requested);
		return get(tenantId, userId);
	}

	public AdminUserData.TenantUser setStatus(String tenantId, String userId, String status) throws QorvaException {
		if (status == null || !SETTABLE.contains(status)) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.ADMIN_INVALID_STATUS, status);
		}
		var user = support.requireUser(tenantId, userId);
		if (UNUSABLE.contains(status)) {
			assertAnotherManager(tenantId, user);
		}
		updateUser(userId, new Update().set("userAccountStatus", status).inc("passwordCredentialVersion", 1));
		audit.record("USER_STATUS_CHANGED", tenantId, userId, user.getEmail() + ": " + user.getUserAccountStatus() + " → " + status);
		return get(tenantId, userId);
	}

	public void resendInvite(String tenantId, String userId) throws QorvaException {
		var user = support.requireUser(tenantId, userId);
		TenantScope.runAs(tenantId, () -> userService.resendInvite(tenantId, userId));
		audit.record("USER_INVITE_RESENT", tenantId, userId, "Invite re-sent to " + user.getEmail());
	}

	public void signOut(String tenantId, String userId) throws QorvaException {
		var user = support.requireUser(tenantId, userId);
		support.endSession(userId);
		audit.record("USER_SIGNED_OUT", tenantId, userId, "Every session of " + user.getEmail() + " ended");
	}

	public AdminUserData.TenantUser resetMfa(String tenantId, String userId) throws QorvaException {
		var user = support.requireUser(tenantId, userId);
		updateUser(userId, new Update().set("mfaEnabled", false).unset("mfaEnabledAt"));
		audit.record("USER_MFA_RESET", tenantId, userId, "Email MFA turned off for " + user.getEmail());
		return get(tenantId, userId);
	}

	/** Soft delete (status DELETED, sessions ended); the address stays taken. */
	public void delete(String tenantId, String userId) throws QorvaException {
		var user = support.requireUser(tenantId, userId);
		assertAnotherManager(tenantId, user);
		updateUser(userId, new Update().set("userAccountStatus", UserStatusEnum.DELETED.getValue()).inc("passwordCredentialVersion", 1));
		audit.record("USER_DELETED", tenantId, userId, user.getEmail() + " deleted (was " + user.getUserAccountStatus() + ")");
	}

	// -------------------------------------------------------------------------

	private AdminUserData.TenantUser get(String tenantId, String userId) throws QorvaException {
		return view(support.requireUser(tenantId, userId), isOwner(tenantId, userId));
	}

	private boolean isOwner(String tenantId, String userId) {
		var users = support.usersOf(tenantId);
		return !users.isEmpty() && users.getFirst().getId().equals(userId);
	}

	/** 409 when {@code user} is the company's last usable user allowed to manage users. */
	private void assertAnotherManager(String tenantId, User user) throws QorvaException {
		if (!isUsableManager(user)) {
			return;
		}
		boolean another = support.usersOf(tenantId).stream()
			.anyMatch(u -> !u.getId().equals(user.getId()) && isUsableManager(u));
		if (!another) {
			throw QorvaErrors.conflict(QorvaErrorCodes.ADMIN_LAST_OWNER);
		}
	}

	private static boolean isUsableManager(User u) {
		return !UNUSABLE.contains(u.getUserAccountStatus()) && allowed(u).contains(UserActionsEnum.MANAGE_USERS.getValue());
	}

	private void updateUser(String userId, Update update) {
		support.mongo().updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(userId))), update, User.class);
	}

	static List<UserAuthority> authorities(List<String> actions, UserRoleEnum role) throws QorvaException {
		var known = Arrays.stream(UserActionsEnum.values()).map(UserActionsEnum::getValue).toList();
		for (var action : actions) {
			if (!known.contains(action)) {
				throw QorvaErrors.badRequest(QorvaErrorCodes.ADMIN_UNKNOWN_ACTION, action);
			}
		}
		return new LinkedHashSet<>(actions).stream()
			.map(a -> UserAuthority.builder().role(role.getValue()).action(a).permission(UserPermissionEnum.ALLOWED.getValue()).build())
			.toList();
	}

	static List<String> allowed(User u) {
		return u.getAuthorities() == null ? List.of() : u.getAuthorities().stream()
			.filter(a -> UserPermissionEnum.ALLOWED.getValue().equals(a.getPermission()))
			.map(UserAuthority::getAction).filter(Objects::nonNull).distinct().toList();
	}

	private static AdminUserData.TenantUser view(User u, boolean owner) {
		return new AdminUserData.TenantUser(u.getId(), u.getTenantId(), u.getFirstName(), u.getLastName(), u.getEmail(),
			u.getUserAccountStatus(), owner, allowed(u), u.isMfaEnabledOrFalse(), u.isInvitePendingOrFalse(), u.getInvitedAt(),
			u.getLastLoginAt(), u.getCreatedAt());
	}
}
