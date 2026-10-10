package ai.qorva.core.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Platform statistics. Test accounts (internal) are left out unless asked for. */
public final class AdminStatsData {

	private AdminStatsData() {
	}

	public record Tenants(long total, Map<String, Long> byStatus, Map<String, Long> byAccountType, Map<String, Long> byPlan) {}

	public record Users(long total, Map<String, Long> byStatus, long mfaEnabled, long invitesPending) {}

	public record JobPosts(long total, long open) {}

	public record Total(long total) {}

	public record BackgroundJobs(long running, long pending, long stuck, long failedLast7Days) {}

	public record StripeEvents(long last7Days, long failedLast7Days) {}

	public record Overview(Tenants tenants, Users users, JobPosts jobPosts, Total cvs, Total matchingReports,
	                       BackgroundJobs backgroundJobs, StripeEvents stripeEvents) {}

	public record Point(String bucket, long count) {}

	public record Entry(String key, long count) {}

	public record Durations(Long p50Ms, Long p95Ms) {}

	public record DomainStats(Instant from, Instant to, String granularity, long total, List<Point> series,
	                          Map<String, List<Entry>> breakdowns, Durations durations) {}
}
