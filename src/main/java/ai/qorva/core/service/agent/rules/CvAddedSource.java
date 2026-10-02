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

	@Override
	public List<RuleSubject> newSubjects(AgentRule rule, Instant since, Instant now, int limit) {
		var criteria = Criteria.where("tenantId").is(new ObjectId(rule.getTenantId()))
			.and("createdAt").gte(since).lte(now)
			.and("archived").ne(true);
		var source = rule.getTrigger().getSource();
		if (AgentRule.Trigger.SOURCE_ATS.equals(source)) {
			criteria = criteria.and("atsRefs.0").exists(true);
		} else if (AgentRule.Trigger.SOURCE_MANUAL.equals(source)) {
			criteria = criteria.and("atsRefs.0").exists(false);
		}
		var query = Query.query(criteria).with(Sort.by("createdAt")).limit(limit);
		query.fields().include("personalInformation.name", "createdAt", "tenantId");
		return mongoTemplate.find(query, CV.class).stream().map(CvAddedSource::subject).toList();
	}

	private static RuleSubject subject(CV cv) {
		var name = RuleText.name(cv.getPersonalInformation() != null ? cv.getPersonalInformation().getName() : null);
		return new RuleSubject(cv.getId(), cv.getCreatedAt(), List.of(new AgentRun.Mention("CV", cv.getId(), name)),
			"candidate " + name + " (cvId=" + cv.getId() + ")");
	}
}
