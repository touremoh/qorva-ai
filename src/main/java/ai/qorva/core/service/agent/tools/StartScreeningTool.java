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
import ai.qorva.core.service.MatchingTopNPolicy;
import ai.qorva.core.service.JobPostService;
import ai.qorva.core.service.UsageMonitoringService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs matching for the chosen open jobs — spends matching actions, so it goes through an approval card showing
 * the jobs, their Top N, the estimated cost (only new or changed reports are charged) and what the plan has left.
 */
@Component
public class StartScreeningTool implements AgentTool {

	static final int MAX_JOBS = 10;

	private final AIScreeningService screeningService;
	private final JobPostService jobPostService;
	private final UsageMonitoringService usageMonitoringService;
	private final MatchingTopNPolicy topNPolicy;

	public StartScreeningTool(AIScreeningService screeningService, JobPostService jobPostService,
	                          UsageMonitoringService usageMonitoringService, MatchingTopNPolicy topNPolicy) {
		this.screeningService = screeningService;
		this.jobPostService = jobPostService;
		this.usageMonitoringService = usageMonitoringService;
		this.topNPolicy = topNPolicy;
	}

	@Override
	public String name() {
		return "start_screening";
	}

	@Override
	public String description() {
		return "Run matching for up to 10 open jobs: scores each job's top N best-fitting candidates and writes matching "
			+ "reports. topN (5, 10, 15… up to the plan's maximum) is optional — by default each job keeps its last Top N, "
			+ "or the plan default. Only new or changed reports cost a matching action (unchanged ones are reused), so the "
			+ "recruiter approves it first. Use list_reports afterwards to read the results.";
	}

	@Override
	public String inputSchema() {
		return """
			{"type":"object","properties":{
			  "jobIds":{"type":"array","items":{"type":"string"},"minItems":1,"maxItems":10},
			  "topN":{"type":"integer","minimum":5,"maximum":30,"multipleOf":5}
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
		var requestedTopN = ToolArgs.optionalInteger(args, "topN");
		if (requestedTopN != null) {
			var max = topNPolicy.limitsFor(ctx.tenantId()).max();
			if (requestedTopN < MatchingTopNPolicy.STEP || requestedTopN % MatchingTopNPolicy.STEP != 0 || requestedTopN > max) {
				return AgentToolResult.error("topN must be a multiple of " + MatchingTopNPolicy.STEP + " between "
					+ MatchingTopNPolicy.STEP + " and " + max + " on this plan.");
			}
		}

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
		var cards = new ArrayList<Map<String, Object>>();
		int estimate = 0;
		int reused = 0;
		for (var job : jobs) {
			int topN = topNPolicy.resolve(ctx.tenantId(), requestedTopN, job.getMatchingTopN());
			var cost = screeningService.estimate(List.of(job), topN, ctx.language()).getFirst();
			estimate += cost.newReports();
			reused += cost.reusedReports();
			cards.add(Map.of("jobId", job.getId(), "title", job.getTitle() != null ? job.getTitle() : "", "topN", topN));
		}
		Integer remaining = usageMonitoringService.remaining(ctx.tenantId(), UsageMonitoringService.FeatureKey.SCREENING_ACTIONS);
		if (remaining != null && remaining < estimate) {
			return AgentToolResult.error("Not enough matching actions left this period: up to " + estimate + " needed, "
				+ remaining + " remaining.");
		}

		var card = new LinkedHashMap<String, Object>();
		card.put("jobs", cards);
		card.put("estimatedActions", estimate);
		card.put("reusedReports", reused);
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
		var screened = screeningService.screenJobs(ctx.tenantId(), ids, ToolArgs.optionalInteger(args, "topN"), ctx.language());
		var data = new LinkedHashMap<String, Object>();
		data.put("screenedJobs", screened.stream().map(j -> Map.of("jobId", j.getId(), "title", j.getTitle() != null ? j.getTitle() : "")).toList());
		data.put("next", "Use list_reports with each jobId to read the new scores.");
		return AgentToolResult.ok(data, "agent.step.screening_done", Map.of("count", String.valueOf(screened.size())),
			screened.stream().map(j -> new AgentRun.Link("JOB", j.getId(), j.getTitle())).toList());
	}
}
