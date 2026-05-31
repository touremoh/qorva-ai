package ai.qorva.core.helpers;

import ai.qorva.core.dto.common.UserAuthority;
import ai.qorva.core.enums.UserActionsEnum;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

import static ai.qorva.core.enums.UserActionsEnum.VIEW_DASHBOARD;
import static ai.qorva.core.enums.UserPermissionEnum.ALLOWED;
import static ai.qorva.core.enums.UserRoleEnum.ACCOUNT_OWNER;

@UtilityClass
public class UserAuthoritiesHelper {
	public List<UserAuthority> createAuthorities() {
		var authorities = new ArrayList<UserAuthority>();

		// DASHBOARD
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), VIEW_DASHBOARD.getValue(), ALLOWED.getValue()));

		// CV
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.ADD_CV.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.VIEW_CV.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.MODIFY_CV.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.DELETE_CV.getValue(), ALLOWED.getValue()));

		// Jobs
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.ADD_JOB.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.VIEW_JOB.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.MODIFY_JOB.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.DELETE_JOB.getValue(), ALLOWED.getValue()));

		// Reports
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.GENERATE_REPORT.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.VIEW_REPORT.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.MODIFY_REPORT.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.DELETE_REPORT.getValue(), ALLOWED.getValue()));

		// Chat
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.START_CHAT.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.VIEW_CHAT.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.VIEW_MESSAGE.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.REPLY_MESSAGE.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.MODIFY_CHAT.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.DELETE_CHAT.getValue(), ALLOWED.getValue()));


		// Users
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.VIEW_USERS.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.MANAGE_USERS.getValue(), ALLOWED.getValue()));

		// ATS Export Reports
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.ATS_REPORT_EXPORT.getValue(), ALLOWED.getValue()));

		// Billing
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.UPDATE_SUBSCRIPTION.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.CANCEL_SUBSCRIPTION.getValue(), ALLOWED.getValue()));

		// Library Insights
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.VIEW_LIBRARY_INSIGHTS.getValue(), ALLOWED.getValue()));

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
