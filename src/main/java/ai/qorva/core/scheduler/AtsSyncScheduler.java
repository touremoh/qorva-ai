package ai.qorva.core.scheduler;

import ai.qorva.core.config.AtsProperties;
import ai.qorva.core.dao.entity.AtsConnection;
import ai.qorva.core.dao.repository.AtsConnectionRepository;
import ai.qorva.core.service.ats.AtsSyncService;
import ai.qorva.core.service.ats.AtsWebhookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Enqueues periodic delta syncs for every connected auto-import connection. The jobs
 * land in the shared BackgroundJob queue, so multi-instance dedup and leasing come for
 * free; enqueueQuietly refuses when a run is already queued or active.
 */
@Slf4j
@Component
public class AtsSyncScheduler {

	private final AtsConnectionRepository connectionRepository;
	private final AtsSyncService syncService;
	private final AtsWebhookService webhookService;
	private final AtsProperties properties;

	public AtsSyncScheduler(AtsConnectionRepository connectionRepository, AtsSyncService syncService,
		AtsWebhookService webhookService, AtsProperties properties) {
		this.connectionRepository = connectionRepository;
		this.syncService = syncService;
		this.webhookService = webhookService;
		this.properties = properties;
	}

	@Scheduled(fixedDelayString = "${qorva.ats.scheduler-poll-delay-ms:300000}")
	public void enqueueDueSyncs() {
		var connections = connectionRepository.findByStatus(AtsConnection.STATUS_CONNECTED);
		var due = Instant.now().minus(properties.getSyncIntervalMinutes(), ChronoUnit.MINUTES);
		for (var connection : connections) {
			// Puts webhooks back when registration failed earlier or the public base URL moved.
			webhookService.reconcile(connection);

			var settings = connection.getSettings();
			if (settings == null || !Boolean.TRUE.equals(settings.getAutoImport())) {
				continue;
			}
			var lastSync = connection.getSyncState() != null ? connection.getSyncState().getLastSyncAt() : null;
			if (lastSync == null || lastSync.isBefore(due)) {
				syncService.enqueueQuietly(connection, AtsSyncService.TRIGGER_SCHEDULED);
			}
		}
	}
}
