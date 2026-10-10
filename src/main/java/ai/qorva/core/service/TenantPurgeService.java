package ai.qorva.core.service;

import ai.qorva.core.dao.entity.BackgroundJob;
import ai.qorva.core.dao.entity.Tenant;
import ai.qorva.core.enums.TenantStatusEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantScope;
import ai.qorva.core.service.cascade.CascadeRegistry;
import ai.qorva.core.service.cascade.PurgeScope;
import ai.qorva.core.service.cascade.TenantAccountPurge;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Deletes a soft-deleted tenant for good: every collection (the cascade registry, {@link PurgeScope#FULL}), its files,
 * then the tenant document. Runs as a {@code TENANT_PURGE} background job — resumable, visible in the console — queued
 * by an admin ("purge now") or by the daily sweep once {@code purgeAfter} has passed. Refuses a tenant that is not
 * DELETED, whatever queued it.
 */
@Slf4j
@Service
public class TenantPurgeService {

	private static final List<String> ACTIVE = List.of(BackgroundJob.STATUS_PENDING, BackgroundJob.STATUS_RUNNING);

	private final MongoTemplate mongoTemplate;
	private final CascadeRegistry cascadeRegistry;
	private final TenantAccountPurge accountPurge;
	private final S3StorageService s3StorageService;
	private final boolean adminEnabled;

	public TenantPurgeService(MongoTemplate mongoTemplate, CascadeRegistry cascadeRegistry, TenantAccountPurge accountPurge,
	                          S3StorageService s3StorageService, @Value("${qorva.admin.enabled:false}") boolean adminEnabled) {
		this.adminEnabled = adminEnabled;
		this.mongoTemplate = mongoTemplate;
		this.cascadeRegistry = cascadeRegistry;
		this.accountPurge = accountPurge;
		this.s3StorageService = s3StorageService;
	}

	/** Queues the purge of a DELETED tenant; one active purge per tenant (409 otherwise). */
	public BackgroundJob enqueue(String tenantId, String createdBy) throws QorvaException {
		var tenant = mongoTemplate.findById(new ObjectId(tenantId), Tenant.class);
		if (tenant == null) {
			throw QorvaErrors.notFound(QorvaErrorCodes.ADMIN_TENANT_NOT_FOUND);
		}
		if (TenantStatusEnum.of(tenant.getStatus()) != TenantStatusEnum.DELETED) {
			throw QorvaErrors.conflict(QorvaErrorCodes.ADMIN_TENANT_NOT_DELETED);
		}
		if (mongoTemplate.exists(Query.query(Criteria.where("tenantId").is(new ObjectId(tenantId))
			.and("type").is(BackgroundJob.TYPE_TENANT_PURGE).and("status").in(ACTIVE)), BackgroundJob.class)) {
			throw QorvaErrors.conflict(QorvaErrorCodes.ADMIN_PURGE_IN_PROGRESS);
		}
		var job = mongoTemplate.insert(BackgroundJob.builder()
			.tenantId(tenantId)
			.type(BackgroundJob.TYPE_TENANT_PURGE)
			.status(BackgroundJob.STATUS_PENDING)
			.errorSamples(List.of())
			.createdBy(createdBy)
			.createdAt(Instant.now())
			.build());
		log.info("Tenant purge queued: tenant={} job={} by={}", tenantId, job.getId(), createdBy);
		return job;
	}

	/** Worker entry point; runs in the tenant's scope (set by the worker). */
	public void execute(BackgroundJob job) {
		var tenantId = job.getTenantId();
		var tenant = mongoTemplate.findById(new ObjectId(tenantId), Tenant.class);
		if (tenant != null && TenantStatusEnum.of(tenant.getStatus()) != TenantStatusEnum.DELETED) {
			log.warn("Tenant purge {} refused: tenant {} is {}, not DELETED", job.getId(), tenantId, tenant.getStatus());
			finish(job, BackgroundJob.STATUS_FAILED, 0, "tenant_not_deleted");
			return;
		}
		var counts = cascadeRegistry.purgeTenant(tenantId, PurgeScope.FULL, false);
		s3StorageService.deleteAllForTenant(tenantId);
		long removed = counts.values().stream().mapToLong(Long::longValue).sum() + accountPurge.removeTenant(tenantId);
		finish(job, BackgroundJob.STATUS_COMPLETED, removed, null);
		log.info("Tenant {} purged by job {}: {} document(s) — {}", tenantId, job.getId(), removed, counts);
	}

	/** Daily: queue the purge of every tenant whose grace period is over. */
	@Scheduled(cron = "${qorva.admin.purge-sweep-cron:0 30 3 * * *}")
	public void sweep() {
		if (!adminEnabled) {
			return;
		}
		TenantScope.runAsSystem("tenant purge sweep", () -> {
			var due = mongoTemplate.find(Query.query(Criteria.where("status").is(TenantStatusEnum.DELETED.name())
				.and("purgeAfter").lte(Instant.now())), Tenant.class);
			for (var tenant : due) {
				try {
					enqueue(tenant.getId(), "system:purge-sweep");
				} catch (QorvaException e) {
					log.debug("Purge of tenant {} not queued: {}", tenant.getId(), e.getMessage());
				}
			}
		});
	}

	private void finish(BackgroundJob job, String status, long removed, String failureReason) {
		var update = new Update().set("status", status).set("finishedAt", Instant.now())
			.set("total", removed).set("processed", removed).set("succeeded", removed);
		if (failureReason != null) {
			update.set("failureReason", failureReason);
		}
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(job.getId())), update, BackgroundJob.class);
	}
}
