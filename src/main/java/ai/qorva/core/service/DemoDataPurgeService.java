package ai.qorva.core.service;

import ai.qorva.core.service.cascade.CascadeRegistry;
import ai.qorva.core.service.cascade.PurgeScope;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Wipes all tenant-scoped recruitment data. Used when a demo tenant upgrades to a paid plan:
 * everything it holds is sample data, so we purge it wholesale to start the real account clean.
 */
@Slf4j
@Service
public class DemoDataPurgeService {

	private final CascadeRegistry cascadeRegistry;
	private final S3StorageService s3StorageService;

	@Autowired
	public DemoDataPurgeService(CascadeRegistry cascadeRegistry, S3StorageService s3StorageService) {
		this.cascadeRegistry = cascadeRegistry;
		this.s3StorageService = s3StorageService;
	}

	/** Deletes every recruitment-related document for the tenant. Best-effort per collection. */
	public void purgeAll(String tenantId) {
		// Every delete is scoped by tenantId; a blank one must never reach them.
		if (tenantId == null || tenantId.isBlank()) {
			throw new IllegalArgumentException("Demo purge requires a tenant id");
		}
		log.info("Purging demo data for tenant={}", tenantId);
		var deleted = cascadeRegistry.purgeTenant(tenantId, PurgeScope.RECRUITMENT, true);
		s3StorageService.deleteCvDocumentsForTenant(tenantId); // best-effort, never throws
		log.info("Demo data purged for tenant={}: {}", tenantId, deleted);
	}
}
