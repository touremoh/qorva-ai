package ai.qorva.core.service.cascade;

import ai.qorva.core.dao.repository.AgentRunRepository;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dao.repository.JobPostRepository;
import ai.qorva.core.dao.repository.QualityIssueStateRepository;
import ai.qorva.core.dao.repository.UsageMonitoringRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collections that only ever go in a tenant-wide purge: the CVs themselves, and what is derived from
 * the library as a whole (Copilot runs, quality-issue states). Job posts and
 * usage periods survive a library clear and go only when all recruitment data does.
 */
@Component
public class TenantDataPurge implements CascadeParticipant {

	private final CVRepository cvs;
	private final AgentRunRepository agentRuns;
	private final QualityIssueStateRepository qualityIssueStates;
	private final JobPostRepository jobPosts;
	private final UsageMonitoringRepository usage;

	public TenantDataPurge(CVRepository cvs, AgentRunRepository agentRuns,
	                QualityIssueStateRepository qualityIssueStates, JobPostRepository jobPosts, UsageMonitoringRepository usage) {
		this.cvs = cvs;
		this.agentRuns = agentRuns;
		this.qualityIssueStates = qualityIssueStates;
		this.jobPosts = jobPosts;
		this.usage = usage;
	}

	@Override
	public Map<String, Long> onTenantPurge(String tenantId, PurgeScope scope) {
		var counts = new LinkedHashMap<String, Long>();
		counts.put("cvs", cvs.deleteByTenantId(tenantId));
		// Runs quote CVs, reports and jobs in their steps and answers: they go with the library.
		counts.put("agent_runs", agentRuns.deleteByTenantId(tenantId));
		counts.put("quality_issue_states", qualityIssueStates.deleteByTenantId(tenantId));
		if (scope == PurgeScope.RECRUITMENT) {
			counts.put("job_posts", jobPosts.deleteByTenantId(tenantId));
			counts.put("usage_monitoring", usage.deleteByTenantId(tenantId));
		}
		return counts;
	}
}
