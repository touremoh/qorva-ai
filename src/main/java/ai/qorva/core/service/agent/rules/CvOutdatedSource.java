package ai.qorva.core.service.agent.rules;

import ai.qorva.core.dao.entity.AgentRule;
import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.entity.CandidateUpdateRequest;
import ai.qorva.core.enums.ContentDateSourceEnum;
import ai.qorva.core.service.CandidateUpdateService;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CVs whose content (Data Health's freshness date, {@code contentDate}) turned {@code staleMonths} old. Nothing is
 * stored when a CV becomes outdated — it is time passing — so the window is shifted back by the age. Keyed on the CV
 * and its content date: a candidate who updates gets a new date, and a new firing once that one ages too. CVs with
 * no known date, archived ones and ones with a request already in progress are left out.
 */
@Component
public class CvOutdatedSource implements AgentTriggerSource {

	private final MongoTemplate mongoTemplate;

	public CvOutdatedSource(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public String type() {
		return AgentRule.TRIGGER_CV_OUTDATED;
	}

	@Override
	public List<RuleSubject> newSubjects(AgentRule rule, Instant since, Instant now, int limit) {
		var trigger = rule.getTrigger();
		if (trigger.getStaleMonths() == null) return List.of();
		int months = trigger.getStaleMonths();
		var criteria = Criteria.where("tenantId").is(new ObjectId(rule.getTenantId()))
			.and("contentDate").gte(minusMonths(since, months)).lte(minusMonths(now, months))
			.and("contentDateSource").ne(ContentDateSourceEnum.UNKNOWN.name())
			.and("archived").ne(true);
		criteria = CvAddedSource.bySource(criteria, trigger.getSource());
		var query = Query.query(criteria).with(Sort.by("contentDate")).limit(limit);
		query.fields().include("personalInformation.name", "contentDate", "tenantId");
		var cvs = mongoTemplate.find(query, CV.class);
		var pending = withActiveRequest(rule.getTenantId(), cvs);
		return cvs.stream().filter(cv -> !pending.contains(cv.getId())).map(cv -> subject(cv, months)).toList();
	}

	static Instant minusMonths(Instant instant, int months) {
		return instant.atOffset(ZoneOffset.UTC).minusMonths(months).toInstant();
	}

	private Set<String> withActiveRequest(String tenantId, List<CV> cvs) {
		if (cvs.isEmpty()) return Set.of();
		var query = Query.query(Criteria.where("tenantId").is(new ObjectId(tenantId))
			.and("cvId").in(cvs.stream().map(CV::getId).toList())
			.and("status").in(CandidateUpdateService.ACTIVE_STATUSES));
		query.fields().include("cvId");
		var ids = new HashSet<String>();
		mongoTemplate.find(query, CandidateUpdateRequest.class).forEach(r -> ids.add(r.getCvId()));
		return ids;
	}

	static RuleSubject subject(CV cv, int months) {
		var name = RuleText.name(cv.getPersonalInformation() != null ? cv.getPersonalInformation().getName() : null);
		var date = cv.getContentDate().atOffset(ZoneOffset.UTC).toLocalDate();
		return new RuleSubject(cv.getId() + ":" + cv.getContentDate().toEpochMilli(),
			cv.getContentDate().atOffset(ZoneOffset.UTC).plusMonths(months).toInstant(),
			List.of(new AgentRun.Mention("CV", cv.getId(), name)),
			"candidate " + name + " (cvId=" + cv.getId() + "), most recent CV content dated " + date);
	}
}
