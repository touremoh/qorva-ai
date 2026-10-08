package ai.qorva.core.service.agent.rules;

import ai.qorva.core.dao.entity.AgentRule;
import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.repository.CVRepository;
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
 * New CVs that share an email or phone with an older one — the same check as the upload's duplicate warning
 * ({@code findContactMatch}), run on every new CV whatever its path (upload, bulk import, ATS). Nothing is stored:
 * the match is looked up when the rule checks. Keyed on the new CV, so each fires once.
 */
@Component
public class DuplicateFoundSource implements AgentTriggerSource {

	private final MongoTemplate mongoTemplate;
	private final CVRepository cvRepository;

	public DuplicateFoundSource(MongoTemplate mongoTemplate, CVRepository cvRepository) {
		this.mongoTemplate = mongoTemplate;
		this.cvRepository = cvRepository;
	}

	@Override
	public String type() {
		return AgentRule.TRIGGER_DUPLICATE_FOUND;
	}

	@Override
	public List<RuleSubject> newSubjects(AgentRule rule, Instant since, Instant now, int limit) {
		var tenantId = new ObjectId(rule.getTenantId());
		var criteria = CvAddedSource.newCvs(tenantId, since, now, rule.getTrigger().getSource());
		var query = Query.query(criteria).with(Sort.by("createdAt")).limit(limit);
		query.fields().include("personalInformation.name", "contactKeys", "createdAt", "tenantId");
		var subjects = new ArrayList<RuleSubject>();
		for (var cv : mongoTemplate.find(query, CV.class)) {
			cvRepository.findContactMatch(tenantId, cv.getContactKeys(), new ObjectId(cv.getId()))
				// Two copies created together match each other: only the newer one fires.
				.filter(older -> older.getCreatedAt() == null || cv.getCreatedAt() == null || !older.getCreatedAt().isAfter(cv.getCreatedAt()))
				.ifPresent(older -> subjects.add(subject(cv, older)));
		}
		return subjects;
	}

	static RuleSubject subject(CV cv, CV older) {
		var name = RuleText.name(cv.getPersonalInformation() != null ? cv.getPersonalInformation().getName() : null);
		var olderName = RuleText.name(older.getPersonalInformation() != null ? older.getPersonalInformation().getName() : null);
		var sameEmail = cv.getContactKeys() != null && older.getContactKeys() != null && cv.getContactKeys().getEmail() != null
			&& cv.getContactKeys().getEmail().equals(older.getContactKeys().getEmail());
		var line = "new candidate " + name + " (cvId=" + cv.getId() + ") has the same " + (sameEmail ? "email" : "phone")
			+ " as existing candidate " + olderName + " (cvId=" + older.getId() + ", added "
			+ (older.getCreatedAt() != null ? older.getCreatedAt().toString().substring(0, 10) : "earlier") + ")";
		return new RuleSubject(cv.getId(), cv.getCreatedAt(),
			List.of(new AgentRun.Mention("CV", cv.getId(), name), new AgentRun.Mention("CV", older.getId(), olderName)), line);
	}
}
