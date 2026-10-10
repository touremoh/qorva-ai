package ai.qorva.core.service.cascade;

import ai.qorva.core.dao.entity.AgentRule;
import ai.qorva.core.dao.repository.AgentRuleFiringRepository;
import ai.qorva.core.dao.repository.AgentRuleRepository;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Copilot standing rules. Clearing the library forgets what the rules fired for and restarts them from now
 * (nothing of the old library is fired again); purging all recruitment data removes the rules. A deleted job
 * pauses the rules that watch it.
 */
@Component
public class AgentRuleCascade implements CascadeParticipant {

	private final AgentRuleRepository rules;
	private final AgentRuleFiringRepository firings;
	private final MongoTemplate mongoTemplate;

	public AgentRuleCascade(AgentRuleRepository rules, AgentRuleFiringRepository firings, MongoTemplate mongoTemplate) {
		this.rules = rules;
		this.firings = firings;
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public List<Deleted> onParentsDeleted(CascadeResource parent, String tenantId, Collection<String> parentIds) {
		if (parent == CascadeResource.JOB_POST && !parentIds.isEmpty()) {
			mongoTemplate.updateMulti(Query.query(Criteria.where("tenantId").is(new ObjectId(tenantId))
					.and("trigger.jobPostId").in(parentIds).and("status").is(AgentRule.STATUS_ACTIVE)),
				new Update().set("status", AgentRule.STATUS_PAUSED).set("pausedReason", AgentRule.PAUSED_JOB_DELETED)
					.set("pausedAt", Instant.now()), AgentRule.class);
		}
		return List.of();
	}

	@Override
	public Map<String, Long> onTenantPurge(String tenantId, PurgeScope scope) {
		var counts = new LinkedHashMap<String, Long>();
		counts.put("agent_rule_firings", firings.deleteByTenantId(tenantId));
		if (scope.covers(PurgeScope.RECRUITMENT)) {
			counts.put("agent_rules", rules.deleteByTenantId(tenantId));
		} else {
			mongoTemplate.updateMulti(Query.query(Criteria.where("tenantId").is(new ObjectId(tenantId))),
				new Update().set("watermark", Instant.now()), AgentRule.class);
		}
		return counts;
	}
}
