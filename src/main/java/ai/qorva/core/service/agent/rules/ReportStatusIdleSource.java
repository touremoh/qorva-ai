package ai.qorva.core.service.agent.rules;

import ai.qorva.core.dao.entity.AgentRule;
import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.entity.MatchingReport;
import ai.qorva.core.enums.ApplicationStatusEnum;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Candidates who stayed in a watched status for {@code idleDays} days. The moment a report "fires" is its last
 * status change plus the idle period — {@code statusChangedAt}, or {@code createdAt} for a report that never left
 * New — so the window [since, now] is shifted back by the period. Keyed on the report and its last change: one
 * firing per idle period, and any later move (by anyone, Copilot included) starts a new one.
 */
@Component
public class ReportStatusIdleSource implements AgentTriggerSource {

	private final MongoTemplate mongoTemplate;

	public ReportStatusIdleSource(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public String type() {
		return AgentRule.TRIGGER_REPORT_STATUS_IDLE;
	}

	@Override
	public List<RuleSubject> newSubjects(AgentRule rule, Instant since, Instant now, int limit) {
		var trigger = rule.getTrigger();
		var statuses = trigger.getToStatuses();
		if (statuses == null || statuses.isEmpty() || trigger.getIdleDays() == null) return List.of();
		var idle = Duration.ofDays(trigger.getIdleDays());
		var from = since.minus(idle);
		var to = now.minus(idle);

		var reports = new ArrayList<MatchingReport>(find(rule, Criteria.where("status").in(statuses)
			.and("statusChangedAt").gte(from).lte(to), "statusChangedAt", limit));
		var newStatus = ApplicationStatusEnum.NEW.getStatus();
		if (statuses.contains(newStatus)) {
			// Never moved: the report has been New since it was written.
			reports.addAll(find(rule, Criteria.where("status").is(newStatus).and("statusChangedAt").exists(false)
				.and("createdAt").gte(from).lte(to), "createdAt", limit));
		}
		var archived = archivedCvs(rule.getTenantId(), reports);
		return reports.stream()
			.filter(r -> r.getCandidateInfo() != null && r.getCandidateInfo().getCandidateId() != null)
			.filter(r -> !archived.contains(r.getCandidateInfo().getCandidateId()))
			.map(r -> subject(r, lastChange(r), idle))
			.sorted(Comparator.comparing(RuleSubject::at))
			.limit(limit)
			.toList();
	}

	private List<MatchingReport> find(AgentRule rule, Criteria criteria, String timeField, int limit) {
		criteria = criteria.and("tenantId").is(new ObjectId(rule.getTenantId())).and("outdated").ne(true);
		if (rule.getTrigger().getJobPostId() != null) {
			criteria = criteria.and("jobPostId").is(new ObjectId(rule.getTrigger().getJobPostId()));
		}
		var query = Query.query(criteria).with(Sort.by(timeField)).limit(limit);
		query.fields().include("jobPostId", "jobPostTitle", "candidateInfo.candidateId", "candidateInfo.candidateName",
			"status", "statusChangedAt", "createdAt", "tenantId");
		return mongoTemplate.find(query, MatchingReport.class);
	}

	/** Archived candidates are out of the pipeline: they never fire. */
	private Set<String> archivedCvs(String tenantId, List<MatchingReport> reports) {
		var ids = reports.stream().map(r -> r.getCandidateInfo() != null ? r.getCandidateInfo().getCandidateId() : null)
			.filter(id -> id != null && ObjectId.isValid(id)).map(ObjectId::new).distinct().toList();
		if (ids.isEmpty()) return Set.of();
		var query = Query.query(Criteria.where("tenantId").is(new ObjectId(tenantId)).and("_id").in(ids).and("archived").is(true));
		query.fields().include("_id");
		var archived = new HashSet<String>();
		mongoTemplate.find(query, CV.class).forEach(cv -> archived.add(cv.getId()));
		return archived;
	}

	static Instant lastChange(MatchingReport report) {
		return report.getStatusChangedAt() != null ? report.getStatusChangedAt() : report.getCreatedAt();
	}

	static RuleSubject subject(MatchingReport report, Instant lastChange, Duration idle) {
		var cvId = report.getCandidateInfo().getCandidateId();
		var name = RuleText.name(report.getCandidateInfo().getCandidateName());
		var job = RuleText.name(report.getJobPostTitle());
		var line = "candidate " + name + " (cvId=" + cvId + ", reportId=" + report.getId() + ") on job " + job
			+ " (jobId=" + report.getJobPostId() + ") has been " + report.getStatus() + " for " + idle.toDays() + " days";
		return new RuleSubject(report.getId() + ":" + lastChange.toEpochMilli(), lastChange.plus(idle),
			List.of(new AgentRun.Mention("CV", cvId, name), new AgentRun.Mention("JOB", report.getJobPostId(), job)), line);
	}
}
