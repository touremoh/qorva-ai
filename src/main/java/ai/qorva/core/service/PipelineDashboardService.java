package ai.qorva.core.service;

import ai.qorva.core.dao.entity.MatchingReport;
import ai.qorva.core.dto.PipelineDashboardData;
import ai.qorva.core.enums.ApplicationStatusEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Recruiter metrics from the reports' status history: moves per status per recruiter, and time to shortlist. */
@Service
public class PipelineDashboardService {

	static final Duration DEFAULT_PERIOD = Duration.ofDays(30);
	static final Duration MAX_PERIOD = Duration.ofDays(366);
	static final String COPILOT = "COPILOT";

	private final MongoTemplate mongoTemplate;

	public PipelineDashboardService(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	public PipelineDashboardData pipeline(String tenantId, Instant from, Instant to, String jobPostId) throws QorvaException {
		var end = to != null ? to : Instant.now();
		var start = from != null ? from : end.minus(DEFAULT_PERIOD);
		if (!start.isBefore(end) || Duration.between(start, end).compareTo(MAX_PERIOD) > 0) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.DASHBOARD_PERIOD_INVALID);
		}
		if (StringUtils.hasText(jobPostId) && !ObjectId.isValid(jobPostId)) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.REPORT_JOB_ID_REQUIRED);
		}
		var scope = Criteria.where("tenantId").is(new ObjectId(tenantId));
		if (StringUtils.hasText(jobPostId)) {
			scope = scope.and("jobPostId").is(new ObjectId(jobPostId));
		}
		return new PipelineDashboardData(start, end, StringUtils.hasText(jobPostId) ? jobPostId : null,
			currentByStatus(scope), recruiters(scope, start, end));
	}

	private Map<String, Long> currentByStatus(Criteria scope) {
		var counts = new LinkedHashMap<String, Long>();
		for (var status : ApplicationStatusEnum.values()) {
			counts.put(status.getStatus(), 0L);
		}
		var agg = Aggregation.newAggregation(Aggregation.match(scope), Aggregation.group("status").count().as("n"));
		for (var row : mongoTemplate.aggregate(agg, MatchingReport.class, Document.class).getMappedResults()) {
			if (row.get("_id") instanceof String status && counts.containsKey(status)) {
				counts.put(status, ((Number) row.get("n")).longValue());
			}
		}
		return counts;
	}

	/** One row per history entry in the period; a report with a move in the period has statusChangedAt ≥ start. */
	private List<PipelineDashboardData.RecruiterPipeline> recruiters(Criteria scope, Instant start, Instant end) {
		var agg = Aggregation.newAggregation(
			Aggregation.match(new Criteria().andOperator(scope, Criteria.where("statusChangedAt").gte(start))),
			Aggregation.project("createdAt", "statusHistory"),
			Aggregation.unwind("statusHistory"),
			Aggregation.match(Criteria.where("statusHistory.at").gte(start).lt(end)));
		var rows = mongoTemplate.aggregate(agg, MatchingReport.class, Document.class).getMappedResults();

		var byRecruiter = new LinkedHashMap<String, Acc>();
		// The first move to Shortlisted per report, attributed to whoever made it.
		var firstShortlist = new HashMap<Object, Document>();
		for (var row : rows) {
			var entry = (Document) row.get("statusHistory");
			var key = recruiterKey(entry.getString("by"));
			var acc = byRecruiter.computeIfAbsent(key, k -> new Acc());
			acc.name = COPILOT.equals(key) ? "Copilot" : Objects.requireNonNullElse(entry.getString("byName"), key);
			acc.moves.merge(entry.getString("status"), 1L, Long::sum);
			if (ApplicationStatusEnum.SHORTLISTED.getStatus().equals(entry.getString("status"))) {
				firstShortlist.merge(row.get("_id"), row, (a, b) -> atOf(b).before(atOf(a)) ? b : a);
			}
		}
		for (var row : firstShortlist.values()) {
			var created = row.getDate("createdAt");
			if (created == null) continue;
			var acc = byRecruiter.get(recruiterKey(((Document) row.get("statusHistory")).getString("by")));
			acc.hoursToShortlist.add((atOf(row).getTime() - created.getTime()) / 3_600_000d);
		}
		return byRecruiter.entrySet().stream()
			.map(e -> new PipelineDashboardData.RecruiterPipeline(e.getKey(), e.getValue().name, e.getValue().moves,
				e.getValue().moves.values().stream().mapToLong(Long::longValue).sum(), median(e.getValue().hoursToShortlist)))
			.sorted(Comparator.comparingLong(PipelineDashboardData.RecruiterPipeline::totalMoves).reversed())
			.toList();
	}

	private static Date atOf(Document row) {
		return ((Document) row.get("statusHistory")).getDate("at");
	}

	static String recruiterKey(String by) {
		if (by == null) return "unknown";
		return MatchingReportService.StatusActor.isCopilot(by) ? COPILOT : by;
	}

	static Double median(List<Double> values) {
		if (values.isEmpty()) return null;
		var sorted = values.stream().sorted().toList();
		int mid = sorted.size() / 2;
		var median = sorted.size() % 2 == 1 ? sorted.get(mid) : (sorted.get(mid - 1) + sorted.get(mid)) / 2;
		return Math.round(median * 10) / 10d;
	}

	private static final class Acc {
		String name;
		final Map<String, Long> moves = new LinkedHashMap<>();
		final List<Double> hoursToShortlist = new ArrayList<>();
	}
}
