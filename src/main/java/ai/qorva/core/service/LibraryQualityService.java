package ai.qorva.core.service;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dto.LibraryQualityReport;
import ai.qorva.core.dto.LibraryQualityReport.ConfidenceCounts;
import ai.qorva.core.dto.LibraryQualityReport.DimensionScore;
import ai.qorva.core.dto.LibraryQualityReport.FieldPresenceCounts;
import ai.qorva.core.dto.LibraryQualityReport.IssueCV;
import ai.qorva.core.dto.LibraryQualityReport.IssueCVPage;
import ai.qorva.core.dto.LibraryQualityReport.Metric;
import ai.qorva.core.dto.LibraryQualityReport.QualityIssue;
import ai.qorva.core.enums.QualityIssueKeyEnum;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Computes the tenant's CV library health report. Deterministic and cheap: pure Mongo
 * aggregations fanned out on the shared dashboard executor — no LLM calls.
 */
@Slf4j
@Service
public class LibraryQualityService {

	private final CVRepository cvRepository;
	private final ExecutorService dashboardExecutor;

	private static final int TIMEOUT_SECONDS = 15;

	// Overall score composition — completeness carries the most operational weight.
	private static final double WEIGHT_COMPLETENESS = 0.4;
	private static final double WEIGHT_FRESHNESS = 0.2;
	private static final double WEIGHT_UNIQUENESS = 0.2;
	private static final double WEIGHT_CONFIDENCE = 0.2;

	// Field weights inside the completeness dimension.
	private static final int WEIGHT_CRITICAL = 3;
	private static final int WEIGHT_IMPORTANT = 2;
	private static final int WEIGHT_ENRICHMENT = 1;

	public static final String BUCKET_UP_TO_DATE = "UP_TO_DATE";
	public static final String BUCKET_REVIEW_SUGGESTED = "REVIEW_SUGGESTED";
	public static final String BUCKET_OUTDATED = "OUTDATED";
	public static final String BUCKET_UNKNOWN = "UNKNOWN";

	@Autowired
	public LibraryQualityService(CVRepository cvRepository, ExecutorService dashboardExecutor) {
		this.cvRepository = cvRepository;
		this.dashboardExecutor = dashboardExecutor;
	}

	public LibraryQualityReport getReport(String tenantId) {
		var tenantObjectId = new ObjectId(tenantId);

		var fieldPresence = CompletableFuture.supplyAsync(
			() -> this.cvRepository.getFieldPresenceByTenantId(tenantObjectId), dashboardExecutor);
		var freshnessBuckets = CompletableFuture.supplyAsync(
			() -> this.cvRepository.getFreshnessBucketsByTenantId(tenantObjectId), dashboardExecutor);
		var confidence = CompletableFuture.supplyAsync(
			() -> this.cvRepository.getParseConfidenceByTenantId(tenantObjectId), dashboardExecutor);
		var emailDuplicates = CompletableFuture.supplyAsync(
			() -> this.cvRepository.findEmailDuplicates(tenantObjectId), dashboardExecutor);
		var phoneDuplicates = CompletableFuture.supplyAsync(
			() -> this.cvRepository.findPhoneDuplicates(tenantObjectId), dashboardExecutor);

		fieldPresence.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> null);
		freshnessBuckets.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> List.of());
		confidence.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> null);
		emailDuplicates.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> List.of());
		phoneDuplicates.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> List.of());

		CompletableFuture.allOf(fieldPresence, freshnessBuckets, confidence, emailDuplicates, phoneDuplicates).join();

		var presence = fieldPresence.join();
		long totalCVs = presence != null ? presence.total() : 0L;

		if (totalCVs == 0) {
			return LibraryQualityReport.builder()
				.totalCVs(0)
				.overallScore(null)
				.completeness(new DimensionScore(0, List.of()))
				.freshness(new DimensionScore(0, List.of()))
				.uniqueness(new DimensionScore(0, List.of()))
				.parseConfidence(new DimensionScore(0, List.of()))
				.issues(List.of())
				.build();
		}

		var buckets = normalizeFreshnessBuckets(freshnessBuckets.join(), totalCVs);
		var confidenceCounts = confidence.join();

		long duplicateGroups = emailDuplicates.join().size() + phoneDuplicates.join().size();
		long duplicateExcess = Math.min(totalCVs,
			sumExcess(emailDuplicates.join().stream().map(g -> g.count()).toList())
				+ sumExcess(phoneDuplicates.join().stream().map(g -> g.count()).toList()));

		var completenessDim = completeness(presence);
		var freshnessDim = freshness(buckets, totalCVs);
		var uniquenessDim = uniqueness(duplicateGroups, duplicateExcess, totalCVs);
		var confidenceDim = parseConfidence(confidenceCounts, totalCVs);

		int overall = (int) Math.round(
			WEIGHT_COMPLETENESS * completenessDim.score()
				+ WEIGHT_FRESHNESS * freshnessDim.score()
				+ WEIGHT_UNIQUENESS * uniquenessDim.score()
				+ WEIGHT_CONFIDENCE * confidenceDim.score());

		return LibraryQualityReport.builder()
			.totalCVs(totalCVs)
			.overallScore(overall)
			.completeness(completenessDim)
			.freshness(freshnessDim)
			.uniqueness(uniquenessDim)
			.parseConfidence(confidenceDim)
			.issues(buildIssues(presence, buckets, confidenceCounts, duplicateGroups))
			.build();
	}

	public IssueCVPage getIssueCVs(String tenantId, QualityIssueKeyEnum issueKey, int pageNumber, int pageSize) throws QorvaException {
		if (issueKey == QualityIssueKeyEnum.DUPLICATES) {
			throw new QorvaException(
				"Duplicates are served by the /cvs/duplicates endpoint",
				HttpStatus.BAD_REQUEST.value(),
				HttpStatus.BAD_REQUEST);
		}
		var page = this.cvRepository.findQualityIssueCVs(
			new ObjectId(tenantId), issueKey, PageRequest.of(pageNumber, pageSize));

		var content = page.getContent().stream().map(this::toIssueCV).toList();
		return new IssueCVPage(
			content,
			pageNumber,
			pageSize,
			page.getTotalElements(),
			page.getTotalPages(),
			page.hasNext());
	}

	private IssueCV toIssueCV(CV cv) {
		var info = cv.getPersonalInformation();
		var contact = info != null ? info.getContact() : null;
		return new IssueCV(
			cv.getId(),
			info != null ? info.getName() : null,
			info != null ? info.getRole() : null,
			contact != null ? contact.getEmail() : null,
			contact != null ? contact.getPhone() : null,
			cv.getLastUpdatedAt(),
			cv.getContentDate());
	}

	// -------------------------------------------------------------------------
	// Dimension scoring
	// -------------------------------------------------------------------------

	private DimensionScore completeness(FieldPresenceCounts presence) {
		long total = presence.total();
		var metrics = new ArrayList<Metric>();
		double weightedSum = 0;
		int weightTotal = 0;

		record Field(String name, long count, int weight) {}
		var fields = List.of(
			new Field("email", presence.hasEmail(), WEIGHT_CRITICAL),
			new Field("phone", presence.hasPhone(), WEIGHT_CRITICAL),
			new Field("name", presence.hasName(), WEIGHT_CRITICAL),
			new Field("role", presence.hasRole(), WEIGHT_CRITICAL),
			new Field("workExperience", presence.hasWorkExperience(), WEIGHT_IMPORTANT),
			new Field("keySkills", presence.hasSkills(), WEIGHT_IMPORTANT),
			new Field("careerStartYear", presence.hasCareerStartYear(), WEIGHT_IMPORTANT),
			new Field("education", presence.hasEducation(), WEIGHT_IMPORTANT),
			new Field("languages", presence.hasLanguages(), WEIGHT_ENRICHMENT),
			new Field("certifications", presence.hasCertifications(), WEIGHT_ENRICHMENT),
			new Field("salaryExpectation", presence.hasSalary(), WEIGHT_ENRICHMENT),
			new Field("linkedin", presence.hasLinkedin(), WEIGHT_ENRICHMENT),
			new Field("summary", presence.hasSummary(), WEIGHT_ENRICHMENT)
		);

		for (var field : fields) {
			double pct = percentage(field.count(), total);
			metrics.add(new Metric(field.name(), field.count(), pct));
			weightedSum += field.weight() * pct;
			weightTotal += field.weight();
		}

		int score = (int) Math.round(weightedSum / weightTotal);
		return new DimensionScore(score, metrics);
	}

	private DimensionScore freshness(Map<String, Long> buckets, long total) {
		long upToDate = buckets.get(BUCKET_UP_TO_DATE);
		long reviewSuggested = buckets.get(BUCKET_REVIEW_SUGGESTED);
		long unknown = buckets.get(BUCKET_UNKNOWN);

		// Unknown-dated CVs are excluded from the score (surfaced as their own issue instead of punished).
		long scorable = total - unknown;
		int score = scorable == 0
			? 100
			: (int) Math.round((upToDate + 0.5 * reviewSuggested) / scorable * 100);

		var metrics = buckets.entrySet().stream()
			.map(e -> new Metric(e.getKey(), e.getValue(), percentage(e.getValue(), total)))
			.toList();
		return new DimensionScore(score, metrics);
	}

	private DimensionScore uniqueness(long duplicateGroups, long duplicateExcess, long total) {
		int score = (int) Math.round(100.0 * (total - duplicateExcess) / total);
		var metrics = List.of(
			new Metric("duplicateGroups", duplicateGroups, 0.0),
			new Metric("duplicateCVs", duplicateExcess, percentage(duplicateExcess, total))
		);
		return new DimensionScore(score, metrics);
	}

	private DimensionScore parseConfidence(ConfidenceCounts counts, long total) {
		long missing = counts != null ? counts.missingClustering() : total;
		long low = counts != null ? counts.lowConfidence() : 0;
		long confident = Math.max(0, total - missing - low);

		int score = (int) Math.round(100.0 * confident / total);
		var metrics = List.of(
			new Metric("confident", confident, percentage(confident, total)),
			new Metric("lowConfidence", low, percentage(low, total)),
			new Metric("missingAnalysis", missing, percentage(missing, total))
		);
		return new DimensionScore(score, metrics);
	}

	// -------------------------------------------------------------------------
	// Issues
	// -------------------------------------------------------------------------

	private List<QualityIssue> buildIssues(
		FieldPresenceCounts presence,
		Map<String, Long> buckets,
		ConfidenceCounts confidence,
		long duplicateGroups
	) {
		long total = presence.total();
		long lowParseConfidence = confidence != null
			? confidence.missingClustering() + confidence.lowConfidence()
			: total;

		var counts = new LinkedHashMap<QualityIssueKeyEnum, Long>();
		counts.put(QualityIssueKeyEnum.MISSING_CONTACT, presence.missingContact());
		counts.put(QualityIssueKeyEnum.MISSING_EMAIL, total - presence.hasEmail());
		counts.put(QualityIssueKeyEnum.MISSING_PHONE, total - presence.hasPhone());
		counts.put(QualityIssueKeyEnum.DUPLICATES, duplicateGroups);
		counts.put(QualityIssueKeyEnum.OUTDATED, buckets.get(BUCKET_OUTDATED));
		counts.put(QualityIssueKeyEnum.LOW_PARSE_CONFIDENCE, lowParseConfidence);
		counts.put(QualityIssueKeyEnum.NO_WORK_EXPERIENCE, total - presence.hasWorkExperience());
		counts.put(QualityIssueKeyEnum.NO_SKILLS, total - presence.hasSkills());
		counts.put(QualityIssueKeyEnum.MISSING_SUMMARY, total - presence.hasSummary());
		counts.put(QualityIssueKeyEnum.UNKNOWN_FRESHNESS, buckets.get(BUCKET_UNKNOWN));

		return counts.entrySet().stream()
			.filter(e -> e.getValue() > 0)
			.map(e -> new QualityIssue(e.getKey().name(), severityOf(e.getKey()), e.getValue()))
			.toList();
	}

	private String severityOf(QualityIssueKeyEnum key) {
		return switch (key) {
			case MISSING_CONTACT -> "CRITICAL";
			case MISSING_EMAIL, MISSING_PHONE, NO_WORK_EXPERIENCE, DUPLICATES, LOW_PARSE_CONFIDENCE, OUTDATED -> "HIGH";
			case NO_SKILLS, MISSING_SUMMARY, UNKNOWN_FRESHNESS -> "MEDIUM";
		};
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private Map<String, Long> normalizeFreshnessBuckets(List<Metric> raw, long total) {
		var buckets = new LinkedHashMap<String, Long>();
		buckets.put(BUCKET_UP_TO_DATE, 0L);
		buckets.put(BUCKET_REVIEW_SUGGESTED, 0L);
		buckets.put(BUCKET_OUTDATED, 0L);
		buckets.put(BUCKET_UNKNOWN, 0L);
		if (raw != null) {
			raw.stream()
				.filter(m -> buckets.containsKey(m.name()))
				.forEach(m -> buckets.put(m.name(), m.count()));
		}
		// Defensive: if the aggregation timed out, treat everything as unknown rather than fresh.
		long counted = buckets.values().stream().mapToLong(Long::longValue).sum();
		if (counted < total) {
			buckets.put(BUCKET_UNKNOWN, buckets.get(BUCKET_UNKNOWN) + (total - counted));
		}
		return buckets;
	}

	private long sumExcess(List<Integer> groupSizes) {
		return groupSizes.stream().mapToLong(size -> Math.max(0, size - 1)).sum();
	}

	private double percentage(long count, long total) {
		if (total == 0) return 0.0;
		return Math.round((double) count / total * 1000.0) / 10.0;
	}
}
