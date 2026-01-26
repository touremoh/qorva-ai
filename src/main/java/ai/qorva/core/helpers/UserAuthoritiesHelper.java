package ai.qorva.core.helpers;

import ai.qorva.core.dto.common.UserAuthority;
import ai.qorva.core.enums.UserActionsEnum;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

import static ai.qorva.core.enums.UserActionsEnum.READ_DASHBOARD;
import static ai.qorva.core.enums.UserAuthoritiesEnum.ALLOWED;
import static ai.qorva.core.enums.UserRoleEnum.ACCOUNT_OWNER;

@UtilityClass
public class UserAuthoritiesHelper {
	public List<UserAuthority> createAuthorities() {
		var authorities = new ArrayList<UserAuthority>();

		// READ_DASHBOARD
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), READ_DASHBOARD.getValue(), ALLOWED.getValue()));

		// CV
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.CREATE_CV.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.READ_CV.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.UPDATE_CV.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.DELETE_CV.getValue(), ALLOWED.getValue()));

		// Jobs
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.CREATE_JOB.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.READ_JOB.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.UPDATE_JOB.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.DELETE_JOB.getValue(), ALLOWED.getValue()));

		// Reports
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.CREATE_REPORT.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.READ_REPORT.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.UPDATE_REPORT.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.DELETE_REPORT.getValue(), ALLOWED.getValue()));

		// Chat
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.CREATE_CHAT.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.READ_CHAT.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.UPDATE_CHAT.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.DELETE_CHAT.getValue(), ALLOWED.getValue()));

		// Billing
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.UPDATE_SUBSCRIPTION.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.CANCEL_SUBSCRIPTION.getValue(), ALLOWED.getValue()));

		return authorities;
	}

	private UserAuthority createAuthority(String role, String action, String permission) {
		var userAuthority = new UserAuthority();
		userAuthority.setRole(role);
		userAuthority.setAction(action);
		userAuthority.setPermission(permission);
		return userAuthority;
	}
}
