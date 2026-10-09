package ai.qorva.core.service.agent.rules;

import ai.qorva.core.dao.entity.AgentRule;
import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dao.entity.JobPost;
import ai.qorva.core.dao.entity.MatchingReport;
import ai.qorva.core.enums.ApplicationStatusEnum;
import ai.qorva.core.enums.JobPostStatusEnum;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Jobs that were closed ({@code statusChangedAt}, written on every open/closed change). Keyed on the job and the time
 * of the change: closing it again after a reopening fires again. Each job comes with the candidates still waiting on
 * it — New, Contacted or Shortlisted — so a rule can tell them the role is filled.
 */
@Component
public class JobClosedSource implements AgentTriggerSource {

	static final List<String> WAITING = List.of(ApplicationStatusEnum.NEW.getStatus(), ApplicationStatusEnum.CONTACTED.getStatus(),
		ApplicationStatusEnum.SHORTLISTED.getStatus());
	static final int MAX_CANDIDATES_PER_JOB = 25;

	private final MongoTemplate mongoTemplate;

	public JobClosedSource(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public String type() {
		return AgentRule.TRIGGER_JOB_CLOSED;
	}

	/** One job per run: its candidate list can be long. */
	@Override
	public int perRun(int batchSize) {
		return 1;
	}

	@Override
	public List<RuleSubject> newSubjects(AgentRule rule, Instant since, Instant now, int limit) {
		var criteria = Criteria.where("tenantId").is(new ObjectId(rule.getTenantId()))
			.and("status").is(JobPostStatusEnum.CLOSED.getStatus())
			.and("statusChangedAt").gte(since).lte(now);
		if (rule.getTrigger().getJobPostId() != null) {
			criteria = criteria.and("_id").is(new ObjectId(rule.getTrigger().getJobPostId()));
		}
		var query = Query.query(criteria).with(Sort.by("statusChangedAt")).limit(limit);
		query.fields().include("title", "statusChangedAt", "tenantId");
		return mongoTemplate.find(query, JobPost.class).stream().map(job -> subject(job, waiting(rule.getTenantId(), job.getId()))).toList();
	}

	private List<MatchingReport> waiting(String tenantId, String jobId) {
		var query = Query.query(Criteria.where("tenantId").is(new ObjectId(tenantId)).and("jobPostId").is(new ObjectId(jobId))
			.and("status").in(WAITING).and("outdated").ne(true)).limit(MAX_CANDIDATES_PER_JOB);
		query.fields().include("candidateInfo.candidateId", "candidateInfo.candidateName", "status", "tenantId");
		return mongoTemplate.find(query, MatchingReport.class);
	}

	static RuleSubject subject(JobPost job, List<MatchingReport> waiting) {
		var title = RuleText.name(job.getTitle());
		var mentions = new ArrayList<AgentRun.Mention>();
		mentions.add(new AgentRun.Mention("JOB", job.getId(), title));
		var line = new StringBuilder("job ").append(title).append(" (jobId=").append(job.getId()).append(") was closed");
		var candidates = waiting.stream().filter(r -> r.getCandidateInfo() != null && r.getCandidateInfo().getCandidateId() != null).toList();
		if (candidates.isEmpty()) {
			line.append("; no candidate is waiting on it");
		} else {
			line.append("; candidates still waiting on it:");
			for (var report : candidates) {
				var name = RuleText.name(report.getCandidateInfo().getCandidateName());
				mentions.add(new AgentRun.Mention("CV", report.getCandidateInfo().getCandidateId(), name));
				line.append("\n  - ").append(name).append(" (cvId=").append(report.getCandidateInfo().getCandidateId())
					.append(", reportId=").append(report.getId()).append(", status ").append(report.getStatus()).append(')');
			}
		}
		return new RuleSubject(job.getId() + ":" + job.getStatusChangedAt().toEpochMilli(), job.getStatusChangedAt(), mentions,
			line.toString());
	}
}
