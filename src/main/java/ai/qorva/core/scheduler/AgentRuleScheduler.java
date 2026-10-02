package ai.qorva.core.scheduler;

import ai.qorva.core.config.AgentProperties;
import ai.qorva.core.service.agent.rules.AgentApprovalDigest;
import ai.qorva.core.service.agent.rules.AgentRuleEngine;
import ai.qorva.core.service.agent.rules.AgentRuleStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Checks Copilot standing rules for new records and starts runs for them; also queues the owners' approval
 * digests. Multi-instance safe: each rule is claimed with a lease and pushed to its next check, so it is
 * looked at once per tick across the fleet. Does nothing unless both the agent and its rules are switched on.
 */
@Slf4j
@Component
public class AgentRuleScheduler {

	/** Bounds one tick, so a backlog can't keep the scheduler thread busy indefinitely. */
	static final int MAX_RULES_PER_TICK = 500;

	private final AgentRuleStore store;
	private final AgentRuleEngine engine;
	private final AgentApprovalDigest digest;
	private final AgentProperties properties;

	public AgentRuleScheduler(AgentRuleStore store, AgentRuleEngine engine, AgentApprovalDigest digest, AgentProperties properties) {
		this.store = store;
		this.engine = engine;
		this.digest = digest;
		this.properties = properties;
	}

	@Scheduled(fixedDelayString = "${qorva.ai.agent.rules.poll-delay-ms:60000}", initialDelayString = "${qorva.ai.agent.rules.poll-delay-ms:60000}")
	public void tick() {
		if (!properties.isEnabled() || !properties.getRules().isEnabled()) {
			return;
		}
		var interval = Duration.ofMillis(properties.getRules().getPollDelayMs());
		for (int i = 0; i < MAX_RULES_PER_TICK; i++) {
			var rule = store.claimDue(Instant.now(), interval);
			if (rule == null) break;
			try {
				var tick = engine.check(rule);
				if (tick.subjects() > 0 || tick.paused() != null) {
					log.info("agent-rule {} tick subjects={} runs={} skipped={} paused={}", rule.getId(), tick.subjects(),
						tick.runs(), tick.skipped(), tick.paused());
				}
			} catch (RuntimeException e) {
				log.error("agent-rule {} tick failed", rule.getId(), e);
			} finally {
				store.release(rule);
			}
		}
		try {
			int queued = digest.queueDue();
			if (queued > 0) log.info("agent approval digests queued: {}", queued);
		} catch (RuntimeException e) {
			log.error("agent approval digests could not be queued", e);
		}
	}
}
