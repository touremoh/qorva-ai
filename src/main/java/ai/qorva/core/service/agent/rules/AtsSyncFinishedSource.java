package ai.qorva.core.service.agent.rules;

import ai.qorva.core.dao.entity.AgentRule;
import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dao.entity.BackgroundJob;
import ai.qorva.core.dao.entity.CV;
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
 * An ATS import that finished with new candidates. One sync is one task; the candidates it created are
 * listed (up to the batch size) so the run can act on them directly.
 */
@Component
public class AtsSyncFinishedSource implements AgentTriggerSource {

	static final int MAX_LISTED = 25;
	private static final List<String> FINISHED = List.of(BackgroundJob.STATUS_COMPLETED, BackgroundJob.STATUS_COMPLETED_WITH_ERRORS);

	private final MongoTemplate mongoTemplate;

	public AtsSyncFinishedSource(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public String type() {
		return AgentRule.TRIGGER_ATS_SYNC_FINISHED;
	}

	@Override
	public int perRun(int batchSize) {
		return 1;
	}

	@Override
	public List<RuleSubject> newSubjects(AgentRule rule, Instant since, Instant now, int limit) {
		var tenantId = new ObjectId(rule.getTenantId());
		var criteria = Criteria.where("tenantId").is(tenantId)
			.and("type").is(BackgroundJob.TYPE_ATS_SYNC)
			.and("finishedAt").gte(since).lte(now)
			.and("status").in(FINISHED)
			.and("succeeded").gt(0);
		if (rule.getTrigger().getConnectionId() != null) {
			criteria = criteria.and("connectionId").is(rule.getTrigger().getConnectionId());
		}
		var query = Query.query(criteria).with(Sort.by("finishedAt")).limit(limit);
		return mongoTemplate.find(query, BackgroundJob.class).stream().map(job -> subject(rule, tenantId, job)).toList();
	}

	private RuleSubject subject(AgentRule rule, ObjectId tenantId, BackgroundJob job) {
		var created = Query.query(Criteria.where("tenantId").is(tenantId)
				.and("atsRefs.connectionId").is(job.getConnectionId())
				.and("createdAt").gte(job.getStartedAt() != null ? job.getStartedAt() : job.getCreatedAt()).lte(job.getFinishedAt()))
			.with(Sort.by("createdAt")).limit(MAX_LISTED);
		created.fields().include("personalInformation.name", "tenantId");
		var mentions = new ArrayList<AgentRun.Mention>();
		var names = new StringBuilder();
		for (var cv : mongoTemplate.find(created, CV.class)) {
			var name = RuleText.name(cv.getPersonalInformation() != null ? cv.getPersonalInformation().getName() : null);
			mentions.add(new AgentRun.Mention("CV", cv.getId(), name));
			names.append("\n  - candidate ").append(name).append(" (cvId=").append(cv.getId()).append(')');
		}
		var connection = rule.getTrigger().getConnectionName() != null ? rule.getTrigger().getConnectionName() : "the ATS";
		var line = "import from " + connection + " finished with " + job.getSucceeded() + " candidate(s) added or updated"
			+ (mentions.isEmpty() ? "" : "; new candidates" + (job.getSucceeded() > MAX_LISTED ? " (first " + MAX_LISTED + ")" : "") + ":" + names);
		return new RuleSubject(job.getId(), job.getFinishedAt(), mentions, line);
	}
}
