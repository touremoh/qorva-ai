package ai.qorva.core.admin.service;

import ai.qorva.core.admin.dto.AdminJobData;
import ai.qorva.core.admin.dto.AdminOpsData;
import ai.qorva.core.admin.dto.AdminPage;
import ai.qorva.core.dao.entity.AdminAuditLog;
import ai.qorva.core.dao.entity.BackgroundJob;
import ai.qorva.core.dao.entity.StripeEventLog;
import ai.qorva.core.dao.entity.StripeWebhookEvent;
import ai.qorva.core.dao.entity.Tenant;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantScope;
import ai.qorva.core.service.BackgroundJobService;
import ai.qorva.core.service.BulkCvUploadService;
import ai.qorva.core.service.MatchingRunService;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Background jobs, Stripe event logs and the admin audit log across every company. */
@Service
public class AdminOpsService {

	private static final Set<String> CANCELLABLE = Set.of(BackgroundJob.STATUS_DRAFT, BackgroundJob.STATUS_PENDING, BackgroundJob.STATUS_RUNNING);

	private final AdminTenantSupport support;
	private final AdminAuditService audit;
	private final BackgroundJobService backgroundJobService;
	private final BulkCvUploadService bulkCvUploadService;
	private final MatchingRunService matchingRunService;

	public AdminOpsService(AdminTenantSupport support, AdminAuditService audit, BackgroundJobService backgroundJobService,
	                       BulkCvUploadService bulkCvUploadService, MatchingRunService matchingRunService) {
		this.support = support;
		this.audit = audit;
		this.backgroundJobService = backgroundJobService;
		this.bulkCvUploadService = bulkCvUploadService;
		this.matchingRunService = matchingRunService;
	}

	// ---- background jobs ------------------------------------------------------

	public AdminPage<AdminJobData.JobRow> jobs(Map<String, String> params) throws QorvaException {
		int page = AdminQueryParams.page(params);
		int size = AdminQueryParams.size(params);
		var criteria = new ArrayList<Criteria>();
		var tenantId = params.get("tenantId");
		if (StringUtils.hasText(tenantId)) {
			criteria.add(Criteria.where("tenantId").is(ObjectId.isValid(tenantId) ? new ObjectId(tenantId) : tenantId));
		}
		AdminQueryParams.equalsIfPresent(criteria, params, "type", "type");
		AdminQueryParams.equalsIfPresent(criteria, params, "status", "status");
		AdminQueryParams.createdBetween(criteria, params, "createdAt");
		if ("true".equalsIgnoreCase(params.get("stuck"))) {
			criteria.add(Criteria.where("status").is(BackgroundJob.STATUS_RUNNING).and("leaseExpiresAt").lt(Instant.now()));
		}
		var query = criteria.isEmpty() ? new Query() : Query.query(new Criteria().andOperator(criteria));
		long total = support.mongo().count(query, BackgroundJob.class);
		query.with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("_id"))).skip((long) page * size).limit(size);
		var jobs = support.mongo().find(query, BackgroundJob.class);
		var names = tenantNames(jobs.stream().map(BackgroundJob::getTenantId).toList());
		return AdminPage.of(jobs.stream().map(j -> AdminJobData.JobRow.from(j, names.get(j.getTenantId()))).toList(), page, size, total);
	}

	public AdminJobData.JobDetail job(String jobId) throws QorvaException {
		var job = requireJob(jobId);
		return AdminJobData.JobDetail.from(job, tenantNames(List.of(job.getTenantId())).get(job.getTenantId()));
	}

	/** Through the job type's own cancel, so what the type holds (staged files…) is released as when the tenant cancels. */
	public AdminJobData.JobDetail cancel(String jobId) throws QorvaException {
		var job = requireJob(jobId);
		if (!CANCELLABLE.contains(job.getStatus())) {
			throw QorvaErrors.conflict(QorvaErrorCodes.ADMIN_JOB_NOT_CANCELLABLE);
		}
		var tenantId = job.getTenantId();
		TenantScope.runAs(tenantId, () -> {
			switch (job.getType()) {
				case BackgroundJob.TYPE_BULK_CV_UPLOAD -> bulkCvUploadService.cancel(tenantId, jobId);
				case BackgroundJob.TYPE_MATCHING -> matchingRunService.cancel(tenantId, jobId);
				default -> backgroundJobService.cancel(tenantId, jobId);
			}
		});
		var after = requireJob(jobId);
		if (!BackgroundJob.STATUS_CANCELLED.equals(after.getStatus())) {
			throw QorvaErrors.conflict(QorvaErrorCodes.ADMIN_JOB_NOT_CANCELLABLE);
		}
		audit.record("JOB_CANCELLED", tenantId, null, job.getType() + " job " + jobId + " cancelled (was " + job.getStatus() + ")");
		return job(jobId);
	}

	private BackgroundJob requireJob(String jobId) throws QorvaException {
		var job = jobId == null || !ObjectId.isValid(jobId) ? null : support.mongo().findById(new ObjectId(jobId), BackgroundJob.class);
		if (job == null) {
			throw QorvaErrors.notFound(QorvaErrorCodes.ADMIN_JOB_NOT_FOUND);
		}
		return job;
	}

	// ---- Stripe events --------------------------------------------------------

	public AdminPage<AdminOpsData.StripeEventRow> stripeEvents(Map<String, String> params) throws QorvaException {
		int page = AdminQueryParams.page(params);
		int size = AdminQueryParams.size(params);
		var criteria = new ArrayList<Criteria>();
		var tenantId = params.get("tenantId");
		if (StringUtils.hasText(tenantId)) {
			criteria.add(Criteria.where("tenantId").is(ObjectId.isValid(tenantId) ? new ObjectId(tenantId) : tenantId));
		}
		AdminQueryParams.equalsIfPresent(criteria, params, "eventType", "eventType");
		AdminQueryParams.equalsIfPresent(criteria, params, "eventStatus", "eventStatus");
		AdminQueryParams.equalsIfPresent(criteria, params, "customerId", "stripeCustomerId");
		AdminQueryParams.createdBetween(criteria, params, "createdAt");
		var query = criteria.isEmpty() ? new Query() : Query.query(new Criteria().andOperator(criteria));
		long total = support.mongo().count(query, StripeEventLog.class);
		query.with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("_id"))).skip((long) page * size).limit(size);
		var events = support.mongo().find(query, StripeEventLog.class);
		var names = tenantNames(events.stream().map(StripeEventLog::getTenantId).toList());
		var inLedger = ledger(events.stream().map(StripeEventLog::getStripeEventId).filter(Objects::nonNull).toList());
		var rows = events.stream().map(e -> new AdminOpsData.StripeEventRow(e.getId(), e.getStripeEventId(), e.getEventType(),
			e.getEventStatus(), e.getTenantId(), names.get(e.getTenantId()), e.getStripeCustomerId(), e.getStripeSubscriptionId(),
			e.getCreatedAt(), e.getStripeEventId() != null && inLedger.contains(e.getStripeEventId()))).toList();
		return AdminPage.of(rows, page, size, total);
	}

	public List<String> stripeEventTypes() {
		return support.mongo().findDistinct(new Query(), "eventType", StripeEventLog.class, String.class).stream()
			.filter(Objects::nonNull).sorted().toList();
	}

	private Set<String> ledger(Collection<String> eventIds) {
		if (eventIds.isEmpty()) {
			return Set.of();
		}
		var query = Query.query(Criteria.where("_id").in(eventIds));
		query.fields().include("_id");
		return support.mongo().find(query, StripeWebhookEvent.class).stream().map(StripeWebhookEvent::getId).collect(Collectors.toSet());
	}

	// ---- audit -----------------------------------------------------------------

	public AdminPage<AdminOpsData.AuditEntry> audit(Map<String, String> params) throws QorvaException {
		int page = AdminQueryParams.page(params);
		int size = AdminQueryParams.size(params);
		var criteria = new ArrayList<Criteria>();
		AdminQueryParams.equalsIfPresent(criteria, params, "tenantId", "targetTenantId");
		AdminQueryParams.equalsIfPresent(criteria, params, "adminId", "adminId");
		AdminQueryParams.equalsIfPresent(criteria, params, "action", "action");
		AdminQueryParams.createdBetween(criteria, params, "at");
		var query = criteria.isEmpty() ? new Query() : Query.query(new Criteria().andOperator(criteria));
		long total = support.mongo().count(query, AdminAuditLog.class);
		query.with(Sort.by(Sort.Order.desc("at"), Sort.Order.desc("_id"))).skip((long) page * size).limit(size);
		return AdminPage.of(support.mongo().find(query, AdminAuditLog.class).stream().map(AdminOpsData.AuditEntry::from).toList(),
			page, size, total);
	}

	// ----------------------------------------------------------------------------

	Map<String, String> tenantNames(Collection<String> tenantIds) {
		var ids = new HashSet<>(tenantIds.stream().filter(Objects::nonNull).toList());
		var names = new HashMap<String, String>();
		if (ids.isEmpty()) {
			return names;
		}
		var query = Query.query(Criteria.where("_id").in(AdminTenantSupport.objectIds(ids)));
		query.fields().include("_id", "tenantName");
		support.mongo().find(query, Tenant.class).forEach(t -> names.put(t.getId(), t.getTenantName()));
		return names;
	}
}
