package ai.qorva.core.service.agent.rules;

import ai.qorva.core.dao.entity.AgentRun;

import java.time.Instant;
import java.util.List;

/**
 * One record a rule fires for: its ledger key, the timestamp the watermark moves to, the records the run
 * may act on (mentions), and the line that describes it to the model.
 */
public record RuleSubject(String key, Instant at, List<AgentRun.Mention> mentions, String line) {}
