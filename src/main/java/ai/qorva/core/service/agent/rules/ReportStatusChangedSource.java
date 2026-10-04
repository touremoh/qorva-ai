package ai.qorva.core.service.agent.rules;

import ai.qorva.core.dao.entity.AgentRule;
import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dao.entity.MatchingReport;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * A candidate's status on a job changed. The ledger key is the report plus the time of the change, so each change
 * fires once; a report moved twice inside one check fires for its latest status. Changes made by Copilot runs
 * ({@code copilot:<runId>}) never fire: a rule that sets statuses can't trigger itself, or another status rule.
 */
@Component
public class ReportStatusChangedSource implements AgentTriggerSource {

	private static final Pattern COPILOT = Pattern.compile("^copilot:");

	private final MongoTemplate mongoTemplate;

	public ReportStatusChangedSource(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public String type() {
		return AgentRule.TRIGGER_REPORT_STATUS_CHANGED;
	}

	@Override
	public List<RuleSubject> newSubjects(AgentRule rule, Instant since, Instant now, int limit) {
		var trigger = rule.getTrigger();
		var criteria = Criteria.where("tenantId").is(new ObjectId(rule.getTenantId()))
			.and("statusChangedAt").gte(since).lte(now)
			.and("statusChangedBy").not().regex(COPILOT);
		if (trigger.getJobPostId() != null) {
			criteria = criteria.and("jobPostId").is(new ObjectId(trigger.getJobPostId()));
		}
		if (trigger.getToStatuses() != null && !trigger.getToStatuses().isEmpty()) {
			criteria = criteria.and("status").in(trigger.getToStatuses());
		}
		var query = Query.query(criteria).with(Sort.by("statusChangedAt")).limit(limit);
		query.fields().include("jobPostId", "jobPostTitle", "candidateInfo.candidateId", "candidateInfo.candidateName",
			"status", "statusChangedAt", "statusChangedBy", "statusHistory", "tenantId");
		return mongoTemplate.find(query, MatchingReport.class).stream()
			.filter(r -> r.getCandidateInfo() != null && r.getCandidateInfo().getCandidateId() != null)
			.map(ReportStatusChangedSource::subject)
			.toList();
	}

	static RuleSubject subject(MatchingReport report) {
		var cvId = report.getCandidateInfo().getCandidateId();
		var name = RuleText.name(report.getCandidateInfo().getCandidateName());
		var job = RuleText.name(report.getJobPostTitle());
		var line = new StringBuilder("candidate ").append(name).append(" (cvId=").append(cvId).append(", reportId=")
			.append(report.getId()).append(") on job ").append(job).append(" (jobId=").append(report.getJobPostId())
			.append(") moved");
		var history = report.getStatusHistory();
		var last = history != null && !history.isEmpty() ? history.get(history.size() - 1) : null;
		if (last != null && last.getFrom() != null) {
			line.append(" from ").append(last.getFrom());
		}
		line.append(" to ").append(report.getStatus());
		if (last != null && last.getByName() != null) {
			line.append(" by ").append(RuleText.name(last.getByName()));
		}
		return new RuleSubject(report.getId() + ":" + report.getStatusChangedAt().toEpochMilli(), report.getStatusChangedAt(),
			List.of(new AgentRun.Mention("CV", cvId, name), new AgentRun.Mention("JOB", report.getJobPostId(), job)),
			line.toString());
	}
}
