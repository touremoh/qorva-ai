package ai.qorva.core.service.agent.tools;

import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.enums.ApplicationStatusEnum;
import ai.qorva.core.enums.UserActionsEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.agent.AgentRiskTier;
import ai.qorva.core.service.agent.AgentTool;
import ai.qorva.core.service.agent.AgentToolContext;
import ai.qorva.core.service.agent.AgentToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import ai.qorva.core.dao.entity.MatchingReport;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Matching reports ranked by score, best first. A direct query because the report service only
 * pages by date; the tenant criterion is always part of it.
 */
@Component
public class ListReportsTool implements AgentTool {

	private static final String SCORE = "matchingReportDetails.decisionSummary.finalScore";

	private final MongoTemplate mongoTemplate;

	public ListReportsTool(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public String name() {
		return "list_reports";
	}

	@Override
	public String description() {
		return "List matching reports (a candidate scored 0-100 against a job), best score first. "
			+ "Filter by job and minimum score to find the top candidates for a job, and by status "
			+ "(where the candidate stands on that job) to find e.g. who is shortlisted.";
	}

	@Override
	public String inputSchema() {
		return """
			{"type":"object","properties":{
			  "jobId":{"type":"string"},
			  "cvId":{"type":"string","description":"Reports of one candidate across jobs"},
			  "minScore":{"type":"number","minimum":0,"maximum":100},
			  "status":{"type":"string","enum":["NEW","CONTACTED","SHORTLISTED","INTERVIEWING","OFFERED","HIRED","REJECTED","WITHDRAWN"]},
			  "page":{"type":"integer","minimum":0},
			  "pageSize":{"type":"integer","minimum":1,"maximum":25}
			},"additionalProperties":false}""";
	}

	@Override
	public AgentRiskTier tier() {
		return AgentRiskTier.READ;
	}

	@Override
	public Set<UserActionsEnum> requiredActions() {
		return Set.of(UserActionsEnum.VIEW_REPORT);
	}

	@Override
	public AgentToolResult execute(JsonNode args, AgentToolContext ctx) throws QorvaException {
		var criteria = Criteria.where("tenantId").is(new ObjectId(ctx.tenantId()));
		var jobId = ToolArgs.text(args, "jobId");
		if (jobId != null) {
			if (!ObjectId.isValid(jobId)) return AgentToolResult.error("Unknown jobId: " + jobId);
			criteria = criteria.and("jobPostId").is(new ObjectId(jobId));
		}
		var cvId = ToolArgs.text(args, "cvId");
		if (cvId != null) criteria = criteria.and("candidateInfo.candidateId").is(cvId);
		if (args != null && args.hasNonNull("minScore") && args.get("minScore").isNumber()) {
			criteria = criteria.and(SCORE).gte(args.get("minScore").asDouble());
		}
		var status = ToolArgs.text(args, "status");
		if (status != null) {
			var parsed = ApplicationStatusEnum.parse(status);
			if (parsed.isEmpty()) return AgentToolResult.error("Unknown status: " + status);
			criteria = criteria.and("status").is(parsed.get().getStatus());
		}
		int page = ToolArgs.integer(args, "page", 0, 0, 1000);
		int size = ToolArgs.integer(args, "pageSize", 10, 1, 25);

		var query = Query.query(criteria);
		long total = mongoTemplate.count(query, MatchingReport.class);
		query.with(Sort.by(Sort.Direction.DESC, SCORE)).skip((long) page * size).limit(size);
		var reports = mongoTemplate.find(query, MatchingReport.class);

		var data = new LinkedHashMap<String, Object>();
		data.put("total", total);
		data.put("reports", reports.stream().map(r -> ReportProjections.summary(r.getId(), r.getJobPostId(),
			r.getJobPostTitle(), r.getCandidateInfo(), r.getMatchingReportDetails(), r.getOutdated(), r.getStatus())).toList());
		var links = reports.stream().limit(10).map(r -> new AgentRun.Link("REPORT", r.getId(),
			r.getCandidateInfo() != null ? r.getCandidateInfo().getCandidateName() : null)).toList();
		return AgentToolResult.ok(data, "agent.step.list_reports", Map.of("count", String.valueOf(total)), links);
	}
}
