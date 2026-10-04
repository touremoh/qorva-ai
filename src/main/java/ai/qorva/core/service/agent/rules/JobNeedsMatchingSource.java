package ai.qorva.core.service.agent.rules;

import ai.qorva.core.dao.entity.AgentRule;
import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dao.entity.JobPost;
import ai.qorva.core.enums.JobPostStatusEnum;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Open jobs whose matching results became out of date (or out of date for a stronger reason) — one "needs
 * matching" episode each, stamped {@code matchingStaleAt} by the job's flagging. The ledger key is the job and
 * its episode, so a job fires once per episode; matching it clears the flag and ends the episode.
 */
@Component
public class JobNeedsMatchingSource implements AgentTriggerSource {

	private final MongoTemplate mongoTemplate;

	public JobNeedsMatchingSource(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public String type() {
		return AgentRule.TRIGGER_JOB_NEEDS_MATCHING;
	}

	@Override
	public List<RuleSubject> newSubjects(AgentRule rule, Instant since, Instant now, int limit) {
		var trigger = rule.getTrigger();
		var criteria = Criteria.where("tenantId").is(new ObjectId(rule.getTenantId()))
			.and("status").is(JobPostStatusEnum.OPEN.getStatus())
			.and("matchingReportsNeeded").is(true)
			.and("matchingStaleAt").gte(since).lte(now);
		if (trigger.getJobPostId() != null) {
			criteria = criteria.and("_id").is(new ObjectId(trigger.getJobPostId()));
		}
		if (trigger.getStaleReasons() != null && !trigger.getStaleReasons().isEmpty()) {
			criteria = criteria.and("matchingStaleReason").in(trigger.getStaleReasons());
		}
		var query = Query.query(criteria).with(Sort.by("matchingStaleAt")).limit(limit);
		query.fields().include("title", "matchingStaleReason", "matchingStaleAt", "matchingTopN", "newCandidateIds", "tenantId");
		return mongoTemplate.find(query, JobPost.class).stream().map(JobNeedsMatchingSource::subject).toList();
	}

	static RuleSubject subject(JobPost job) {
		var title = RuleText.name(job.getTitle());
		var line = new StringBuilder("job ").append(title).append(" (jobId=").append(job.getId()).append(") needs matching: ")
			.append(RuleRunMessage.reasonText(job.getMatchingStaleReason() != null ? job.getMatchingStaleReason() : ""));
		if (job.getNewCandidateIds() != null && !job.getNewCandidateIds().isEmpty()) {
			line.append(", ").append(job.getNewCandidateIds().size()).append(" new candidate(s)");
		}
		if (job.getMatchingTopN() != null) {
			line.append(", last matched with the top ").append(job.getMatchingTopN());
		}
		return new RuleSubject(job.getId() + ":" + job.getMatchingStaleAt().toEpochMilli(), job.getMatchingStaleAt(),
			List.of(new AgentRun.Mention("JOB", job.getId(), title)), line.toString());
	}
}
