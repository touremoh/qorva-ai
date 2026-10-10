package ai.qorva.core.admin.service;

import ai.qorva.core.admin.dto.AdminStatsData;
import ai.qorva.core.admin.dto.AdminTenantData;
import ai.qorva.core.dao.entity.BackgroundJob;
import ai.qorva.core.enums.JobPostStatusEnum;
import ai.qorva.core.enums.TenantAccountTypeEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Platform statistics: one aggregation per figure, cached five minutes per instance and per question. Counts what was
 * created in the range; buckets are UTC days, weeks (Monday) or months.
 */
@Service
public class AdminStatsService {

	static final int MAX_SPAN_DAYS = 366;
	private static final Set<String> GRANULARITIES = Set.of("day", "week", "month");
	private static final Map<String, String> COLLECTIONS = Map.of("tenants", "tenants", "users", "users", "job-posts", "job_posts",
		"cvs", "cvs", "background-jobs", "background_jobs", "stripe-events", "stripe_event_logs");
	private static final String PAYMENT_FAILED = "invoice.payment_failed";

	private final AdminTenantSupport support;
	private final AdminOpsService ops;
	private final Cache<String, Object> cache = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).maximumSize(500).build();

	public AdminStatsService(AdminTenantSupport support, AdminOpsService ops) {
		this.support = support;
		this.ops = ops;
	}

	public AdminStatsData.Overview overview(boolean includeInternal) {
		return (AdminStatsData.Overview) cache.get("overview:" + includeInternal, k -> computeOverview(includeInternal));
	}

	public AdminStatsData.DomainStats domain(String domain, Instant from, Instant to, String granularity, boolean includeInternal) throws QorvaException {
		if (!COLLECTIONS.containsKey(domain)) {
			throw QorvaErrors.notFound(QorvaErrorCodes.HTTP_NOT_FOUND);
		}
		var end = to != null ? to : Instant.now();
		var start = from != null ? from : end.minus(30, ChronoUnit.DAYS);
		var unit = granularity == null ? "day" : granularity;
		if (!start.isBefore(end) || Duration.between(start, end).toDays() > MAX_SPAN_DAYS || !GRANULARITIES.contains(unit)) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.ADMIN_STATS_RANGE, MAX_SPAN_DAYS);
		}
		var key = String.join("|", domain, start.toString(), end.toString(), unit, String.valueOf(includeInternal));
		return (AdminStatsData.DomainStats) cache.get(key, k -> computeDomain(domain, start, end, unit, includeInternal));
	}

	// ---- overview ---------------------------------------------------------------

	private AdminStatsData.Overview computeOverview(boolean includeInternal) {
		var internal = includeInternal ? List.<ObjectId>of() : internalTenantIds();
		var tenantScope = includeInternal ? new Document() : new Document("internal", new Document("$ne", true));
		var ofTenants = tenantFilter(internal);
		var weekAgo = Date.from(Instant.now().minus(7, ChronoUnit.DAYS));

		var tenantTotal = count("tenants", tenantScope);
		var testers = count("tenants", and(tenantScope, new Document("accountType", TenantAccountTypeEnum.TESTER.name())));
		var demo = count("tenants", and(tenantScope, new Document("_id", new Document("$in", AdminTenantSupport.objectIds(support.demoTenantIds(null))))
			.append("accountType", new Document("$ne", TenantAccountTypeEnum.TESTER.name()))));
		var byType = new LinkedHashMap<String, Long>();
		byType.put(AdminTenantData.TYPE_CUSTOMER, tenantTotal - testers - demo);
		byType.put(AdminTenantData.TYPE_DEMO, demo);
		byType.put(AdminTenantData.TYPE_TESTER, testers);

		var tenants = new AdminStatsData.Tenants(tenantTotal,
			groupCount("tenants", tenantScope, new Document("$ifNull", List.of("$status", "ACTIVE"))), byType,
			groupCount("tenants", tenantScope, new Document("$ifNull", List.of("$subscriptionInfo.subscriptionPlan", "none"))));
		var users = new AdminStatsData.Users(count("users", ofTenants),
			groupCount("users", ofTenants, "$userAccountStatus"),
			count("users", and(ofTenants, new Document("mfaEnabled", true))),
			count("users", and(ofTenants, new Document("invitePending", true))));
		var jobPosts = new AdminStatsData.JobPosts(count("job_posts", ofTenants),
			count("job_posts", and(ofTenants, new Document("status", JobPostStatusEnum.OPEN.getStatus()))));
		var jobs = new AdminStatsData.BackgroundJobs(
			count("background_jobs", and(ofTenants, new Document("status", BackgroundJob.STATUS_RUNNING))),
			count("background_jobs", and(ofTenants, new Document("status", BackgroundJob.STATUS_PENDING))),
			count("background_jobs", and(ofTenants, new Document("status", BackgroundJob.STATUS_RUNNING)
				.append("leaseExpiresAt", new Document("$lt", new Date())))),
			count("background_jobs", and(ofTenants, new Document("status", BackgroundJob.STATUS_FAILED)
				.append("createdAt", new Document("$gte", weekAgo)))));
		var stripe = new AdminStatsData.StripeEvents(
			count("stripe_event_logs", and(ofTenants, new Document("createdAt", new Document("$gte", weekAgo)))),
			count("stripe_event_logs", and(ofTenants, new Document("createdAt", new Document("$gte", weekAgo)).append("eventType", PAYMENT_FAILED))));
		return new AdminStatsData.Overview(tenants, users, jobPosts,
			new AdminStatsData.Total(count("cvs", ofTenants)),
			new AdminStatsData.Total(count("matching_reports", and(ofTenants, new Document("outdated", new Document("$ne", true))))),
			jobs, stripe);
	}

	// ---- per domain ---------------------------------------------------------------

	private AdminStatsData.DomainStats computeDomain(String domain, Instant from, Instant to, String unit, boolean includeInternal) {
		var collection = COLLECTIONS.get(domain);
		var internal = includeInternal ? List.<ObjectId>of() : internalTenantIds();
		Document scope = "tenants".equals(collection)
			? (includeInternal ? new Document() : new Document("internal", new Document("$ne", true)))
			: tenantFilter(internal);
		var match = and(scope, new Document("createdAt", new Document("$gte", Date.from(from)).append("$lte", Date.from(to))));

		var counted = new LinkedHashMap<String, Long>();
		aggregate(collection, List.of(new Document("$match", match),
			new Document("$group", new Document("_id", new Document("$dateTrunc", new Document("date", "$createdAt").append("unit", unit)
				.append("timezone", "UTC").append("startOfWeek", "monday"))).append("n", new Document("$sum", 1)))))
			.forEach(d -> counted.put(bucket(d.getDate("_id").toInstant()), ((Number) d.get("n")).longValue()));
		var series = new ArrayList<AdminStatsData.Point>();
		long total = 0;
		for (var b = truncate(from, unit); !b.isAfter(to.atZone(ZoneOffset.UTC).toLocalDate()); b = next(b, unit)) {
			var n = counted.getOrDefault(b.toString(), 0L);
			series.add(new AdminStatsData.Point(b.toString(), n));
			total += n;
		}

		var breakdowns = new LinkedHashMap<String, List<AdminStatsData.Entry>>();
		switch (domain) {
			case "tenants" -> {
				breakdowns.put("status", entries(groupCount(collection, match, new Document("$ifNull", List.of("$status", "ACTIVE")))));
				breakdowns.put("accountType", entries(groupCount(collection, match, new Document("$ifNull", List.of("$accountType", "CUSTOMER_OR_DEMO")))));
				breakdowns.put("plan", entries(groupCount(collection, match, new Document("$ifNull", List.of("$subscriptionInfo.subscriptionPlan", "none")))));
				breakdowns.put("subscriptionStatus", entries(groupCount(collection, match, new Document("$ifNull", List.of("$subscriptionInfo.subscriptionStatus", "none")))));
			}
			case "users" -> {
				breakdowns.put("status", entries(groupCount(collection, match, "$userAccountStatus")));
				breakdowns.put("mfa", entries(groupCount(collection, match, new Document("$cond", List.of(new Document("$eq", List.of("$mfaEnabled", true)), "enabled", "disabled")))));
			}
			case "job-posts" -> {
				breakdowns.put("status", entries(groupCount(collection, match, "$status")));
				breakdowns.put("topTenants", topTenants(collection, match));
			}
			case "cvs" -> breakdowns.put("topTenants", topTenants(collection, match));
			case "background-jobs" -> {
				breakdowns.put("type", entries(groupCount(collection, match, "$type")));
				breakdowns.put("status", entries(groupCount(collection, match, "$status")));
			}
			default -> {
				breakdowns.put("eventType", entries(groupCount(collection, match, "$eventType")));
				breakdowns.put("eventStatus", entries(groupCount(collection, match, "$eventStatus")));
			}
		}
		var durations = "background-jobs".equals(domain) ? durations(match) : null;
		return new AdminStatsData.DomainStats(from, to, unit, total, series, breakdowns, durations);
	}

	private AdminStatsData.Durations durations(Document match) {
		var finished = and(match, new Document("startedAt", new Document("$ne", null)).append("finishedAt", new Document("$ne", null)));
		var millis = new ArrayList<Long>();
		support.mongo().getCollection("background_jobs").find(finished)
			.projection(new Document("startedAt", 1).append("finishedAt", 1)).limit(10_000)
			.forEach(d -> millis.add(d.getDate("finishedAt").getTime() - d.getDate("startedAt").getTime()));
		if (millis.isEmpty()) {
			return new AdminStatsData.Durations(null, null);
		}
		millis.sort(Long::compare);
		return new AdminStatsData.Durations(percentile(millis, 50), percentile(millis, 95));
	}

	static long percentile(List<Long> sorted, int p) {
		int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
		return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
	}

	private List<AdminStatsData.Entry> topTenants(String collection, Document match) {
		var top = new ArrayList<Document>();
		aggregate(collection, List.of(new Document("$match", match), new Document("$group", new Document("_id", "$tenantId").append("n", new Document("$sum", 1))),
			new Document("$sort", new Document("n", -1)), new Document("$limit", 10))).forEach(top::add);
		var names = ops.tenantNames(top.stream().map(d -> String.valueOf(d.get("_id") instanceof ObjectId o ? o.toHexString() : d.get("_id"))).toList());
		return top.stream().map(d -> {
			var id = d.get("_id") instanceof ObjectId o ? o.toHexString() : String.valueOf(d.get("_id"));
			return new AdminStatsData.Entry(names.getOrDefault(id, id), ((Number) d.get("n")).longValue());
		}).toList();
	}

	// ---- helpers ------------------------------------------------------------------

	private List<ObjectId> internalTenantIds() {
		var ids = new ArrayList<ObjectId>();
		support.mongo().getCollection("tenants").find(new Document("internal", true)).projection(new Document("_id", 1))
			.forEach(d -> ids.add(d.getObjectId("_id")));
		return ids;
	}

	private static Document tenantFilter(List<ObjectId> internal) {
		return internal.isEmpty() ? new Document() : new Document("tenantId", new Document("$nin", internal));
	}

	private static Document and(Document a, Document b) {
		var merged = new Document(a);
		b.forEach((k, v) -> merged.merge(k, v, (x, y) -> x instanceof Document dx && y instanceof Document dy ? merge(dx, dy) : y));
		return merged;
	}

	private static Document merge(Document a, Document b) {
		var m = new Document(a);
		m.putAll(b);
		return m;
	}

	private long count(String collection, Bson filter) {
		return support.mongo().getCollection(collection).countDocuments(filter);
	}

	private Iterable<Document> aggregate(String collection, List<Document> pipeline) {
		return support.mongo().getCollection(collection).aggregate(pipeline);
	}

	private Map<String, Long> groupCount(String collection, Document match, Object key) {
		var result = new LinkedHashMap<String, Long>();
		aggregate(collection, List.of(new Document("$match", match), new Document("$group", new Document("_id", key).append("n", new Document("$sum", 1))),
			new Document("$sort", new Document("n", -1))))
			.forEach(d -> result.put(String.valueOf(d.get("_id")), ((Number) d.get("n")).longValue()));
		return result;
	}

	private static List<AdminStatsData.Entry> entries(Map<String, Long> counts) {
		return counts.entrySet().stream().map(e -> new AdminStatsData.Entry(e.getKey(), e.getValue())).toList();
	}

	static LocalDate truncate(Instant instant, String unit) {
		var date = instant.atZone(ZoneOffset.UTC).toLocalDate();
		return switch (unit) {
			case "week" -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
			case "month" -> date.withDayOfMonth(1);
			default -> date;
		};
	}

	private static LocalDate next(LocalDate bucket, String unit) {
		return switch (unit) {
			case "week" -> bucket.plusWeeks(1);
			case "month" -> bucket.plusMonths(1);
			default -> bucket.plusDays(1);
		};
	}

	private static String bucket(Instant instant) {
		return instant.atZone(ZoneOffset.UTC).toLocalDate().toString();
	}
}
