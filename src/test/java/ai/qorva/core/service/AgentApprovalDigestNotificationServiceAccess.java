package ai.qorva.core.service;

import ai.qorva.core.dao.entity.AgentRun;

import java.util.List;

/** Test access to the digest's package-private row rendering. */
public final class AgentApprovalDigestNotificationServiceAccess {

	private AgentApprovalDigestNotificationServiceAccess() {}

	public static String rows(List<AgentRun> runs) {
		return AgentApprovalDigestNotificationService.rows(runs);
	}
}
