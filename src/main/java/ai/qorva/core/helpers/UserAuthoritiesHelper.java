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

		// Users
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.VIEW_USERS.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.MANAGE_USERS.getValue(), ALLOWED.getValue()));

		// ATS Export Reports
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.ATS_REPORT_EXPORT.getValue(), ALLOWED.getValue()));

		// ATS Integrations
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.MANAGE_INTEGRATIONS.getValue(), ALLOWED.getValue()));

		// Billing
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.UPDATE_SUBSCRIPTION.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.CANCEL_SUBSCRIPTION.getValue(), ALLOWED.getValue()));

		// Candidate outreach
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.CONTACT_CANDIDATE.getValue(), ALLOWED.getValue()));

		// Copilot: candidate questions, library analyses and actions
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.USE_AGENT.getValue(), ALLOWED.getValue()));

		return authorities;
	}

	/**
	 * Restricted authority set for demo users: read-only browsing, matching-report generation and Copilot.
	 * Excludes any create/modify/delete of CVs and job posts, user management, ATS export and all billing
	 * actions — so Copilot offers them its read tools, its answers and matching only. Password change is not authority-gated so demo users can still do it.
	 */
	public List<UserAuthority> createDemoAuthorities() {
		var authorities = new ArrayList<UserAuthority>();

		// Dashboard + read-only browsing of seeded sample data
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), VIEW_DASHBOARD.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.VIEW_CV.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.VIEW_JOB.getValue(), ALLOWED.getValue()));

		// Matching reports: generate + view (the only write a demo user may perform)
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.GENERATE_REPORT.getValue(), ALLOWED.getValue()));
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.VIEW_REPORT.getValue(), ALLOWED.getValue()));

		// Copilot: its tools are filtered by the authorities above
		authorities.add(createAuthority(ACCOUNT_OWNER.getValue(), UserActionsEnum.USE_AGENT.getValue(), ALLOWED.getValue()));

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
