package ai.qorva.core.service.agent.rules;

import ai.qorva.core.dao.entity.AgentRule;
import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.entity.CandidateUpdateRequest;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Candidates who completed a profile-update request (Data Health's campaign or Copilot's request_profile_update).
 * Keyed on the request. The request points to the CV as it is now — a new document when the candidate uploaded a
 * newer CV — and a request whose CV was deleted since is skipped.
 */
@Component
public class CandidateProfileUpdatedSource implements AgentTriggerSource {

	private final MongoTemplate mongoTemplate;

	public CandidateProfileUpdatedSource(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public String type() {
		return AgentRule.TRIGGER_CANDIDATE_PROFILE_UPDATED;
	}

	@Override
	public List<RuleSubject> newSubjects(AgentRule rule, Instant since, Instant now, int limit) {
		var query = Query.query(Criteria.where("tenantId").is(new ObjectId(rule.getTenantId()))
			.and("status").is(CandidateUpdateRequest.STATUS_COMPLETED)
			.and("completedAt").gte(since).lte(now)).with(Sort.by("completedAt")).limit(limit);
		query.fields().include("cvId", "completedAt", "tenantId");
		var requests = mongoTemplate.find(query, CandidateUpdateRequest.class);
		var cvIds = requests.stream().map(CandidateUpdateRequest::getCvId).filter(ObjectId::isValid).map(ObjectId::new).distinct().toList();
		if (cvIds.isEmpty()) return List.of();
		var cvQuery = Query.query(Criteria.where("tenantId").is(new ObjectId(rule.getTenantId())).and("_id").in(cvIds));
		cvQuery.fields().include("personalInformation.name", "tenantId");
		Map<String, CV> cvs = mongoTemplate.find(cvQuery, CV.class).stream().collect(Collectors.toMap(CV::getId, Function.identity()));
		return requests.stream().filter(r -> cvs.containsKey(r.getCvId())).map(r -> subject(r, cvs.get(r.getCvId()))).toList();
	}

	static RuleSubject subject(CandidateUpdateRequest request, CV cv) {
		var name = RuleText.name(cv.getPersonalInformation() != null ? cv.getPersonalInformation().getName() : null);
		return new RuleSubject(request.getId(), request.getCompletedAt(), List.of(new AgentRun.Mention("CV", cv.getId(), name)),
			"candidate " + name + " (cvId=" + cv.getId() + ") updated their profile");
	}
}
