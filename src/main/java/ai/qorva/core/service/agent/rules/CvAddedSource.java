package ai.qorva.core.service.agent.rules;

import ai.qorva.core.dao.entity.AgentRule;
import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dao.entity.CV;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** New CVs (uploaded, bulk-uploaded or imported), optionally only imported or only uploaded ones. */
@Component
public class CvAddedSource implements AgentTriggerSource {

	private final MongoTemplate mongoTemplate;

	public CvAddedSource(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public String type() {
		return AgentRule.TRIGGER_CV_ADDED;
	}

	/**
	 * A CV is looked at once it is a minute old: long enough for a candidate's own update (a newer CV replacing the old
	 * one) to be marked as such, so it is not taken for a new candidate.
	 */
	static final Duration SETTLE = Duration.ofMinutes(1);

	@Override
	public List<RuleSubject> newSubjects(AgentRule rule, Instant since, Instant now, int limit) {
		var criteria = newCvs(new ObjectId(rule.getTenantId()), since, now, rule.getTrigger().getSource());
		var query = Query.query(criteria).with(Sort.by("createdAt")).limit(limit);
		query.fields().include("personalInformation.name", "createdAt", "tenantId");
		return mongoTemplate.find(query, CV.class).stream().map(CvAddedSource::subject).toList();
	}

	/**
	 * New, active CVs created in the window (less the settle time), from the chosen source. A CV a candidate uploaded
	 * through a profile-update request replaces their old one: it is not a new candidate.
	 */
	static Criteria newCvs(ObjectId tenantId, Instant since, Instant now, String source) {
		var criteria = Criteria.where("tenantId").is(tenantId)
			.and("createdAt").gte(since.minus(SETTLE)).lte(now.minus(SETTLE))
			.and("archived").ne(true)
			.and("origin").ne(CV.ORIGIN_CANDIDATE_UPDATE);
		return bySource(criteria, source);
	}

	static Criteria bySource(Criteria criteria, String source) {
		if (AgentRule.Trigger.SOURCE_ATS.equals(source)) {
			return criteria.and("atsRefs.0").exists(true);
		}
		if (AgentRule.Trigger.SOURCE_MANUAL.equals(source)) {
			return criteria.and("atsRefs.0").exists(false);
		}
		return criteria;
	}

	private static RuleSubject subject(CV cv) {
		var name = RuleText.name(cv.getPersonalInformation() != null ? cv.getPersonalInformation().getName() : null);
		return new RuleSubject(cv.getId(), cv.getCreatedAt(), List.of(new AgentRun.Mention("CV", cv.getId(), name)),
			"candidate " + name + " (cvId=" + cv.getId() + ")");
	}
}
