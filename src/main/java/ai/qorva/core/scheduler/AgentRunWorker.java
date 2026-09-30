package ai.qorva.core.scheduler;

import ai.qorva.core.config.AgentProperties;
import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.service.agent.AgentRunStore;
import ai.qorva.core.service.agent.AgentRunner;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Executes Copilot runs. Its own worker, not a BackgroundJobWorker job type: that worker runs one job
 * per tick fleet-wide, and a run waiting on the model for minutes would hold up bulk uploads and ATS
 * syncs. Multi-instance safe through the lease in {@link AgentRunStore}; each run executes on its
 * own virtual thread, at most {@code qorva.ai.agent.worker-concurrency} per instance.
 */
@Slf4j
@Component
public class AgentRunWorker {

	private final AgentRunStore store;
	private final AgentRunner runner;
	private final AgentProperties properties;
	private final Semaphore slots;
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	public AgentRunWorker(AgentRunStore store, AgentRunner runner, AgentProperties properties) {
		this.store = store;
		this.runner = runner;
		this.properties = properties;
		this.slots = new Semaphore(Math.max(1, properties.getWorkerConcurrency()));
	}

	@Scheduled(fixedDelayString = "${qorva.ai.agent.poll-delay-ms:5000}")
	public void poll() {
		if (!properties.isEnabled()) {
			return;
		}
		try {
			long expired = store.expireApprovals();
			if (expired > 0) {
				log.info("agent runs expired waiting for approval: {}", expired);
			}
		} catch (RuntimeException e) {
			log.error("agent worker could not expire approvals", e);
		}
		while (slots.tryAcquire()) {
			AgentRun run;
			try {
				run = store.claimNext();
			} catch (RuntimeException e) {
				slots.release();
				log.error("agent worker could not claim a run", e);
				return;
			}
			if (run == null) {
				slots.release();
				return;
			}
			log.info("agent-run {} claimed (tenant={} status={})", run.getId(), run.getTenantId(), run.getStatus());
			executor.execute(() -> {
				try {
					runner.run(run);
				} finally {
					slots.release();
				}
			});
		}
	}

	/** A new run shouldn't wait for the next scheduled poll. */
	public void wakeUp() {
		if (properties.isEnabled()) {
			executor.execute(this::poll);
		}
	}

	@PreDestroy
	void shutdown() {
		executor.shutdown();
	}
}
