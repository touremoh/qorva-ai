package ai.qorva.core.scheduler;

import ai.qorva.core.service.MatchingStalenessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Runs the matching staleness sweep: new and edited CVs flag only the jobs whose top N they would enter.
 * Multi-instance safe without a lease — the flags it raises are idempotent.
 */
@Slf4j
@Component
public class MatchingStalenessScheduler {

	private final MatchingStalenessService stalenessService;

	public MatchingStalenessScheduler(MatchingStalenessService stalenessService) {
		this.stalenessService = stalenessService;
	}

	@Scheduled(fixedDelayString = "${qorva.matching.staleness.poll-delay-ms:60000}",
		initialDelayString = "${qorva.matching.staleness.poll-delay-ms:60000}")
	public void tick() {
		try {
			var outcome = stalenessService.sweep(Instant.now());
			if (outcome.checked() > 0 || outcome.fallbacks() > 0) {
				log.info("matching staleness: {} CV(s) checked, {} job flag(s) raised, {} fallback(s) (no embedding)",
					outcome.checked(), outcome.jobsFlagged(), outcome.fallbacks());
			}
		} catch (RuntimeException e) {
			log.error("matching staleness sweep failed", e);
		}
	}
}
