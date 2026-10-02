package ai.qorva.core.service.agent.rules;

import ai.qorva.core.dao.entity.AgentRule;

import java.time.Instant;
import java.util.List;

/**
 * Finds what is new for a rule since its watermark, oldest first. Called inside the rule's tenant scope;
 * every query also names the tenant explicitly.
 */
public interface AgentTriggerSource {

	/** AgentRule.TRIGGER_* handled. */
	String type();

	/** At most {@code limit} records with a timestamp at or after {@code since} (ties are deduplicated by the ledger). */
	List<RuleSubject> newSubjects(AgentRule rule, Instant since, Instant now, int limit);

	/** Records handed to one run; a sync or a schedule slot is one task on its own. */
	default int perRun(int batchSize) {
		return batchSize;
	}
}
