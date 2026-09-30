package ai.qorva.core.service.agent.tools;

import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.enums.UserActionsEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.agent.AgentApproval;
import ai.qorva.core.service.agent.AgentRiskTier;
import ai.qorva.core.service.agent.AgentTool;
import ai.qorva.core.service.agent.AgentToolContext;
import ai.qorva.core.service.agent.AgentToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.enums.JobPostStatusEnum;
import ai.qorva.core.service.AIScreeningService;
import ai.qorva.core.service.CVService;
import ai.qorva.core.service.JobPostService;
import ai.qorva.core.service.UsageMonitoringService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs matching for the chosen open jobs — spends matching actions, so it goes through an approval card showing
 * the jobs, the estimated cost and what the plan has left.
 */
@Component
public class StartScreeningTool implements AgentTool {

	static final int MAX_JOBS = 10;

	private final AIScreeningService screeningService;
	private final JobPostService jobPostService;
	private final UsageMonitoringService usageMonitoringService;

	public StartScreeningTool(AIScreeningService screeningService, JobPostService jobPostService,
	                          UsageMonitoringService usageMonitoringService) {
		this.screeningService = screeningService;
		this.jobPostService = jobPostService;
		this.usageMonitoringService = usageMonitoringService;
	}

	@Override
	public String name() {
		return "start_screening";
	}

	@Override
	public String description() {
		return "Run matching for up to 10 open jobs: scores the best-fitting candidates against each job and writes "
			+ "matching reports. Costs up to " + CVService.DEFAULT_MATCH_LIMIT + " matching actions per job, so the "
			+ "recruiter approves it first. Use list_reports afterwards to read the results.";
	}

	@Override
	public String inputSchema() {
		return """
			{"type":"object","properties":{
			  "jobIds":{"type":"array","items":{"type":"string"},"minItems":1,"maxItems":10}
			},"required":["jobIds"],"additionalProperties":false}""";
	}

	@Override
	public AgentRiskTier tier() {
		return AgentRiskTier.APPROVAL;
	}

	@Override
	public Set<UserActionsEnum> requiredActions() {
		return Set.of(UserActionsEnum.GENERATE_REPORT);
	}

	@Override
	public AgentToolResult preview(JsonNode args, AgentToolContext ctx) throws QorvaException {
		var ids = ToolArgs.list(args, "jobIds").stream().distinct().toList();
		if (ids.isEmpty()) return AgentToolResult.error("jobIds is required");
		if (ids.size() > MAX_JOBS) return AgentToolResult.error("At most " + MAX_JOBS + " jobs at a time");

		var jobs = new ArrayList<JobPostDTO>();
		for (var id : ids) {
			JobPostDTO job;
			try {
				job = jobPostService.findOneById(id);
			} catch (QorvaException e) {
				return AgentToolResult.error("Unknown job: " + id);
			}
			if (!JobPostStatusEnum.OPEN.getStatus().equals(job.getStatus())) {
				return AgentToolResult.error("The job \"" + job.getTitle() + "\" is closed; only open jobs can be matched.");
			}
			jobs.add(job);
		}
		int estimate = jobs.size() * CVService.DEFAULT_MATCH_LIMIT;
		Integer remaining = remainingActions(ctx.tenantId());
		if (remaining != null && remaining < estimate) {
			return AgentToolResult.error("Not enough matching actions left this period: up to " + estimate + " needed, "
				+ remaining + " remaining.");
		}

		var card = new LinkedHashMap<String, Object>();
		card.put("jobs", jobs.stream().map(j -> Map.of("jobId", j.getId(), "title", j.getTitle() != null ? j.getTitle() : "")).toList());
		card.put("estimatedActions", estimate);
		card.put("remainingActions", remaining);
		return AgentToolResult.ok(card, "agent.step.start_screening", Map.of("count", String.valueOf(jobs.size())),
			jobs.stream().map(j -> new AgentRun.Link("JOB", j.getId(), j.getTitle())).toList());
	}

	@Override
	public AgentToolResult execute(JsonNode args, AgentToolContext ctx) {
		return AgentToolResult.error("Matching needs the recruiter's approval.");
	}

	@Override
	public AgentToolResult execute(JsonNode args, AgentToolContext ctx, AgentApproval approval) throws QorvaException {
		var ids = ToolArgs.list(args, "jobIds").stream().distinct().toList();
		var screened = screeningService.screenJobs(ctx.tenantId(), ids, ctx.language());
		var data = new LinkedHashMap<String, Object>();
		data.put("screenedJobs", screened.stream().map(j -> Map.of("jobId", j.getId(), "title", j.getTitle() != null ? j.getTitle() : "")).toList());
		data.put("next", "Use list_reports with each jobId to read the new scores.");
		return AgentToolResult.ok(data, "agent.step.screening_done", Map.of("count", String.valueOf(screened.size())),
			screened.stream().map(j -> new AgentRun.Link("JOB", j.getId(), j.getTitle())).toList());
	}

	private Integer remainingActions(String tenantId) {
		return usageMonitoringService.findCurrentPeriodByTenantId(tenantId)
			.map(p -> p.getFeatures() != null ? p.getFeatures().getScreeningActions() : null)
			.filter(m -> m != null && m.getLimit() != null)
			.map(m -> Math.max(0, m.getLimit() - (m.getConsumed() != null ? m.getConsumed() : 0)))
			.orElse(null);
	}
}
