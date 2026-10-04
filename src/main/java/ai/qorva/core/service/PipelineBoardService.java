package ai.qorva.core.service;

import ai.qorva.core.dao.entity.MatchingReport;
import ai.qorva.core.dto.PipelineBoardData;
import ai.qorva.core.enums.ApplicationStatusEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The pipeline board, built to stay fast when it holds thousands of reports: exact counts from one aggregation, and
 * each column paged on its own with a keyset cursor (sort value + id), so a card moving elsewhere never shifts a page.
 * New is ordered by best score; every other column by its most recent move.
 */
@Service
public class PipelineBoardService {

	static final int FIRST_PAGE = 20;
	static final int MAX_PAGE = 50;
	static final String SCORE = "matchingReportDetails.decisionSummary.finalScore";
	static final String RECOMMENDATION = "matchingReportDetails.decisionSummary.recommendation";
	static final String CHANGED_AT = "statusChangedAt";

	private final MongoTemplate mongoTemplate;

	public PipelineBoardService(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	/** What narrows the board: one job or all, a candidate name, and whether outdated reports show. */
	public record Filter(String jobPostId, String q, boolean hideOutdated) {
	}

	public PipelineBoardData.Board board(String tenantId, Filter filter) throws QorvaException {
		var counts = counts(tenantId, filter);
		var columns = new ArrayList<PipelineBoardData.Column>();
		for (var status : ApplicationStatusEnum.values()) {
			columns.add(page(tenantId, status, filter, null, FIRST_PAGE, counts.getOrDefault(status.getStatus(), 0L)));
		}
		return new PipelineBoardData.Board(columns);
	}

	public PipelineBoardData.Column column(String tenantId, String status, Filter filter, String cursor, Integer size)
		throws QorvaException {
		var parsed = ApplicationStatusEnum.parse(status)
			.orElseThrow(() -> QorvaErrors.badRequest(QorvaErrorCodes.REPORT_STATUS_INVALID));
		int pageSize = size == null ? FIRST_PAGE : Math.max(1, Math.min(MAX_PAGE, size));
		long count = mongoTemplate.count(Query.query(scope(tenantId, filter).and("status").is(parsed.getStatus())), MatchingReport.class);
		return page(tenantId, parsed, filter, cursor, pageSize, count);
	}

	Map<String, Long> counts(String tenantId, Filter filter) throws QorvaException {
		var agg = Aggregation.newAggregation(Aggregation.match(scope(tenantId, filter)), Aggregation.group("status").count().as("n"));
		var counts = new HashMap<String, Long>();
		for (var row : mongoTemplate.aggregate(agg, MatchingReport.class, Document.class).getMappedResults()) {
			if (row.get("_id") instanceof String s) counts.put(s, ((Number) row.get("n")).longValue());
		}
		return counts;
	}

	private PipelineBoardData.Column page(String tenantId, ApplicationStatusEnum status, Filter filter, String cursor,
	                                      int size, long count) throws QorvaException {
		var sortField = sortField(status);
		var criteria = scope(tenantId, filter).and("status").is(status.getStatus());
		if (StringUtils.hasText(cursor)) {
			criteria = new Criteria().andOperator(criteria, after(sortField, decode(cursor, status)));
		}
		var query = Query.query(criteria)
			.with(Sort.by(Sort.Order.desc(sortField), Sort.Order.desc("_id")))
			.limit(size + 1);
		query.fields().include("_id", "jobPostId", "jobPostTitle", "candidateInfo.candidateId", "candidateInfo.candidateName",
			SCORE, RECOMMENDATION, "outdated", "status", CHANGED_AT, "createdAt").slice("statusHistory", -1);
		var found = mongoTemplate.find(query, MatchingReport.class);
		boolean more = found.size() > size;
		var items = found.stream().limit(size).map(PipelineBoardService::card).toList();
		var next = more ? encode(items.getLast(), status) : null;
		return new PipelineBoardData.Column(status.getStatus(), count, items, next);
	}

	private Criteria scope(String tenantId, Filter filter) throws QorvaException {
		var criteria = Criteria.where("tenantId").is(new ObjectId(tenantId));
		if (StringUtils.hasText(filter.jobPostId())) {
			if (!ObjectId.isValid(filter.jobPostId())) throw QorvaErrors.badRequest(QorvaErrorCodes.REPORT_JOB_ID_REQUIRED);
			criteria = criteria.and("jobPostId").is(new ObjectId(filter.jobPostId()));
		}
		if (StringUtils.hasText(filter.q())) {
			criteria = criteria.and("candidateInfo.candidateName").regex(Pattern.quote(filter.q().strip()), "i");
		}
		if (filter.hideOutdated()) {
			criteria = criteria.and("outdated").ne(true);
		}
		return criteria;
	}

	static String sortField(ApplicationStatusEnum status) {
		return status == ApplicationStatusEnum.NEW ? SCORE : CHANGED_AT;
	}

	/**
	 * After (value, id) in a descending sort where missing values come last: a lower value, the same value with a
	 * lower id, or no value at all; from a card without a value, only cards without a value and a lower id.
	 */
	static Criteria after(String field, Cursor cursor) {
		var id = new ObjectId(cursor.id());
		if (cursor.value() == null) {
			return new Criteria().andOperator(Criteria.where(field).is(null), Criteria.where("_id").lt(id));
		}
		return new Criteria().orOperator(
			Criteria.where(field).lt(cursor.value()),
			new Criteria().andOperator(Criteria.where(field).is(cursor.value()), Criteria.where("_id").lt(id)),
			Criteria.where(field).is(null));
	}

	/** Sort value (a score, or a date for every other column) and the id of the last card returned. */
	record Cursor(Object value, String id) {
	}

	static String encode(PipelineBoardData.Card last, ApplicationStatusEnum status) {
		String value = status == ApplicationStatusEnum.NEW
			? (last.score() == null ? "" : String.valueOf(last.score()))
			: (last.statusChangedAt() == null ? "" : String.valueOf(last.statusChangedAt().toEpochMilli()));
		return Base64.getUrlEncoder().withoutPadding().encodeToString((value + "|" + last.id()).getBytes(StandardCharsets.UTF_8));
	}

	static Cursor decode(String cursor, ApplicationStatusEnum status) throws QorvaException {
		try {
			var parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
			if (parts.length != 2 || !ObjectId.isValid(parts[1])) throw new IllegalArgumentException();
			Object value = parts[0].isEmpty() ? null
				: status == ApplicationStatusEnum.NEW ? Double.valueOf(parts[0]) : Date.from(Instant.ofEpochMilli(Long.parseLong(parts[0])));
			return new Cursor(value, parts[1]);
		} catch (IllegalArgumentException e) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.PIPELINE_CURSOR_INVALID);
		}
	}

	private static PipelineBoardData.Card card(MatchingReport r) {
		var candidate = r.getCandidateInfo();
		var decision = r.getMatchingReportDetails() != null ? r.getMatchingReportDetails().getDecisionSummary() : null;
		var history = r.getStatusHistory();
		return new PipelineBoardData.Card(r.getId(), r.getJobPostId(), r.getJobPostTitle(),
			candidate != null ? candidate.getCandidateId() : null,
			candidate != null ? candidate.getCandidateName() : null,
			decision != null ? decision.getFinalScore() : null,
			decision != null ? decision.getRecommendation() : null,
			Boolean.TRUE.equals(r.getOutdated()),
			ApplicationStatusEnum.parse(r.getStatus()).map(ApplicationStatusEnum::getStatus).orElse(ApplicationStatusEnum.NEW.getStatus()),
			r.getStatusChangedAt(), r.getCreatedAt(),
			history != null && !history.isEmpty() ? history.getLast() : null);
	}
}
