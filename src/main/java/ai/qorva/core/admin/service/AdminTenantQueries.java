package ai.qorva.core.admin.service;

import ai.qorva.core.admin.dto.AdminPage;
import ai.qorva.core.admin.dto.AdminTenantData;
import ai.qorva.core.dao.entity.Tenant;
import ai.qorva.core.dto.common.UsageFeatureMetrics;
import ai.qorva.core.enums.JobPostStatusEnum;
import ai.qorva.core.enums.TenantAccountTypeEnum;
import ai.qorva.core.enums.TenantStatusEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantScope;
import ai.qorva.core.service.UsageMonitoringService;
import ai.qorva.core.utils.Paging;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** The company list and detail of the admin console. Read-only. */
@Service
public class AdminTenantQueries {

	private static final Set<String> SORTABLE = Set.of("createdAt", "tenantName");

	private final AdminTenantSupport support;
	private final UsageMonitoringService usageMonitoringService;

	public AdminTenantQueries(AdminTenantSupport support, UsageMonitoringService usageMonitoringService) {
		this.support = support;
		this.usageMonitoringService = usageMonitoringService;
	}

	public AdminPage<AdminTenantData.TenantRow> list(Map<String, String> params) {
		int page = Paging.page(Paging.param(params, "page", 0));
		int size = Paging.size(Paging.param(params, "size", 25));
		var criteria = new ArrayList<Criteria>();

		var q = params.get("q");
		if (StringUtils.hasText(q)) {
			var pattern = Pattern.compile(Pattern.quote(q.trim()), Pattern.CASE_INSENSITIVE);
			criteria.add(new Criteria().orOperator(Criteria.where("tenantName").regex(pattern), Criteria.where("contactEmail").regex(pattern)));
		}
		var status = params.get("status");
		if (StringUtils.hasText(status)) {
			criteria.add(TenantStatusEnum.of(status) == TenantStatusEnum.ACTIVE
				? new Criteria().orOperator(Criteria.where("status").is(TenantStatusEnum.ACTIVE.name()), Criteria.where("status").exists(false), Criteria.where("status").is(null))
				: Criteria.where("status").is(status));
		}
		if (StringUtils.hasText(params.get("subscriptionStatus"))) {
			criteria.add(Criteria.where("subscriptionInfo.subscriptionStatus").is(params.get("subscriptionStatus")));
		}
		if (StringUtils.hasText(params.get("plan"))) {
			criteria.add(Criteria.where("subscriptionInfo.subscriptionPlan").is(params.get("plan")));
		}
		if ("false".equalsIgnoreCase(params.get("includeInternal"))) {
			criteria.add(Criteria.where("internal").ne(true));
		}
		var type = params.get("accountType");
		if (StringUtils.hasText(type)) {
			criteria.add(accountTypeCriteria(type));
		}

		var query = criteria.isEmpty() ? new Query() : Query.query(new Criteria().andOperator(criteria));
		long total = support.mongo().count(query, Tenant.class);
		query.with(sort(params.get("sort"))).skip((long) page * size).limit(size);
		var tenants = support.mongo().find(query, Tenant.class);

		var ids = tenants.stream().map(Tenant::getId).toList();
		var demo = support.demoTenantIds(ids);
		var users = support.userCounts(ids);
		var rows = tenants.stream().map(t -> row(t, demo, users.getOrDefault(t.getId(), 0L))).toList();
		return AdminPage.of(rows, page, size, total);
	}

	public AdminTenantData.TenantDetail detail(String tenantId) throws QorvaException {
		var t = support.requireTenant(tenantId);
		var demo = support.demoTenantIds(List.of(tenantId));
		var counts = counts(tenantId);
		var row = row(t, demo, counts.users());
		var sub = t.getSubscriptionInfo();
		var subscription = sub == null ? null : new AdminTenantData.Subscription(sub.getSubscriptionPlan(), sub.getBillingCycle(),
			sub.getPriceId(), sub.getSubscriptionStatus(), sub.getSubscriptionId(), sub.getCurrentPeriodStart(),
			sub.getCurrentPeriodEnd(), sub.getCancelAtPeriodEnd());
		return new AdminTenantData.TenantDetail(row.id(), row.tenantName(), row.contactEmail(), row.status(), row.accountType(),
			row.internal(), row.accessExpiresAt(), row.subscriptionPlan(), row.subscriptionStatus(), row.userCount(), row.createdAt(),
			t.getOrganizationId(), t.getRecruitmentType(), t.getOrganizationSize(), t.getCompanyAddress(), t.getPhoneNumber(),
			t.getWebsiteUrl(), t.getSsoRequired(), t.getStripeCustomerId(), subscription, t.getStatusReason(), t.getStatusChangedAt(),
			t.getStatusChangedBy(), t.getDeletedAt(), t.getPurgeAfter(), counts, usage(tenantId), t.getLastUpdatedAt());
	}

	private Criteria accountTypeCriteria(String type) {
		if (AdminTenantData.TYPE_TESTER.equals(type)) {
			return Criteria.where("accountType").is(TenantAccountTypeEnum.TESTER.name());
		}
		var demoIds = AdminTenantSupport.objectIds(support.demoTenantIds(null));
		var notTester = Criteria.where("accountType").ne(TenantAccountTypeEnum.TESTER.name());
		return AdminTenantData.TYPE_DEMO.equals(type)
			? new Criteria().andOperator(notTester, Criteria.where("_id").in(demoIds))
			: new Criteria().andOperator(notTester, Criteria.where("_id").nin(demoIds));
	}

	private static Sort sort(String sort) {
		if (!StringUtils.hasText(sort)) {
			return Sort.by(Sort.Order.desc("createdAt"));
		}
		var parts = sort.split(",");
		var field = SORTABLE.contains(parts[0]) ? parts[0] : "createdAt";
		var dir = parts.length > 1 && "asc".equalsIgnoreCase(parts[1]) ? Sort.Direction.ASC : Sort.Direction.DESC;
		return Sort.by(new Sort.Order(dir, field), Sort.Order.asc("_id"));
	}

	private static AdminTenantData.TenantRow row(Tenant t, Set<String> demo, long users) {
		var sub = t.getSubscriptionInfo();
		return new AdminTenantData.TenantRow(t.getId(), t.getTenantName(), t.getContactEmail(), TenantStatusEnum.of(t.getStatus()).name(),
			AdminTenantSupport.accountType(t, demo), Boolean.TRUE.equals(t.getInternal()), t.getAccessExpiresAt(),
			sub != null ? sub.getSubscriptionPlan() : null, sub != null ? sub.getSubscriptionStatus() : null, users, t.getCreatedAt());
	}

	private AdminTenantData.Counts counts(String tenantId) {
		var mongo = support.mongo();
		var oid = new ObjectId(tenantId);
		var ofTenant = Criteria.where("tenantId").is(oid);
		return new AdminTenantData.Counts(
			mongo.count(Query.query(ofTenant), "users"),
			mongo.count(Query.query(Criteria.where("tenantId").is(oid)), "cvs"),
			mongo.count(Query.query(Criteria.where("tenantId").is(oid)), "job_posts"),
			mongo.count(Query.query(Criteria.where("tenantId").is(oid).and("status").is(JobPostStatusEnum.OPEN.getStatus())), "job_posts"),
			mongo.count(Query.query(Criteria.where("tenantId").is(oid).and("outdated").ne(true)), "matching_reports"));
	}

	private AdminTenantData.Usage usage(String tenantId) {
		var current = TenantScope.callAs(tenantId, () -> usageMonitoringService.findCurrentPeriodByTenantId(tenantId));
		if (current.isEmpty() || current.get().getFeatures() == null) {
			return null;
		}
		var f = current.get().getFeatures();
		var used = new LinkedHashMap<String, Integer>();
		var limits = new LinkedHashMap<String, Integer>();
		put(used, limits, "screeningActions", f.getScreeningActions());
		put(used, limits, "aiResumeChats", f.getAiResumeChats());
		put(used, limits, "talentIntelligenceQueries", f.getTalentIntelligenceQueries());
		put(used, limits, "agentRuns", f.getAgentRuns());
		return new AdminTenantData.Usage(current.get().getCurrentPeriodStart(), current.get().getCurrentPeriodEnd(), used, limits);
	}

	private static void put(Map<String, Integer> used, Map<String, Integer> limits, String key, UsageFeatureMetrics m) {
		used.put(key, m != null && m.getConsumed() != null ? m.getConsumed() : 0);
		limits.put(key, m != null ? m.getLimit() : null);
	}
}
