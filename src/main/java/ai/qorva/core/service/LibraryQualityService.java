package ai.qorva.core.service;

import ai.qorva.core.config.CacheConfig;
import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.entity.QualityIssueState;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dao.repository.QualityIssueStateRepository;
import ai.qorva.core.dto.CVDuplicatesData;
import ai.qorva.core.dto.LibraryQualityReport;
import ai.qorva.core.dto.LibraryQualityReport.DimensionScore;
import ai.qorva.core.dto.LibraryQualityReport.IssueCV;
import ai.qorva.core.dto.LibraryQualityReport.IssueCVPage;
import ai.qorva.core.dto.LibraryQualityReport.Metric;
import ai.qorva.core.dto.LibraryQualityReport.QualityIssue;
import ai.qorva.core.enums.QualityFlagEnum;
import ai.qorva.core.enums.QualityIssueKeyEnum;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static ai.qorva.core.enums.QualityFlagEnum.*;

/**
 * Computes the tenant's CV library health report. Deterministic and cheap: every read
 * is index-backed (qualityFlags / contentDate / contact indexes — see V20260727_01),
 * fanned out on the shared dashboard executor, and cached per tenant (5 min TTL,
 * evicted on every quality-affecting mutation).
 */
@Slf4j
@Service
public class LibraryQualityService {

	private final CVRepository cvRepository;
	private final QualityIssueStateRepository issueStateRepository;
	private final LibraryQualityCacheEvictor cacheEvictor;
	private final ExecutorService dashboardExecutor;

	private static final int TIMEOUT_SECONDS = 15;

	/** Verification must stay proportional to actual human attention — never criteria-bulk. */
	public static final int MAX_CONFIRM_CURRENT_PER_CALL = 50;

	public static final String ACTION_ARCHIVE = "ARCHIVE";
	public static final String ACTION_UNARCHIVE = "UNARCHIVE";
	public static final String ACTION_CONFIRM_CURRENT = "CONFIRM_CURRENT";

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

	/** Completeness fields: UI metric name → (flag, weight). Order defines metric order. */
	private record CompletenessField(String metricName, QualityFlagEnum flag, int weight) {}

	private static final List<CompletenessField> COMPLETENESS_FIELDS = List.of(
		new CompletenessField("email", MISSING_EMAIL, WEIGHT_CRITICAL),
		new CompletenessField("phone", MISSING_PHONE, WEIGHT_CRITICAL),
		new CompletenessField("name", MISSING_NAME, WEIGHT_CRITICAL),
		new CompletenessField("role", MISSING_ROLE, WEIGHT_CRITICAL),
		new CompletenessField("workExperience", NO_WORK_EXPERIENCE, WEIGHT_IMPORTANT),
		new CompletenessField("keySkills", NO_SKILLS, WEIGHT_IMPORTANT),
		new CompletenessField("careerStartYear", MISSING_CAREER_START_YEAR, WEIGHT_IMPORTANT),
		new CompletenessField("education", MISSING_EDUCATION, WEIGHT_IMPORTANT),
		new CompletenessField("languages", MISSING_LANGUAGES, WEIGHT_ENRICHMENT),
		new CompletenessField("certifications", MISSING_CERTIFICATIONS, WEIGHT_ENRICHMENT),
		new CompletenessField("salaryExpectation", MISSING_SALARY, WEIGHT_ENRICHMENT),
		new CompletenessField("linkedin", MISSING_LINKEDIN, WEIGHT_ENRICHMENT),
		new CompletenessField("summary", MISSING_SUMMARY, WEIGHT_ENRICHMENT)
	);

	@Autowired
	public LibraryQualityService(
		CVRepository cvRepository,
		QualityIssueStateRepository issueStateRepository,
		LibraryQualityCacheEvictor cacheEvictor,
		ExecutorService dashboardExecutor
	) {
		this.cvRepository = cvRepository;
		this.issueStateRepository = issueStateRepository;
		this.cacheEvictor = cacheEvictor;
		this.dashboardExecutor = dashboardExecutor;
	}

	@Cacheable(cacheNames = CacheConfig.LIBRARY_QUALITY_CACHE, key = "#tenantId")
	public LibraryQualityReport getReport(String tenantId) {
		var tenantObjectId = new ObjectId(tenantId);

		var totalActive = CompletableFuture.supplyAsync(
			() -> this.cvRepository.countActiveByTenantId(tenantObjectId), dashboardExecutor);
		var flagCounts = CompletableFuture.supplyAsync(
			() -> this.cvRepository.countQualityFlagsByTenantId(tenantObjectId), dashboardExecutor);
		var freshnessBuckets = CompletableFuture.supplyAsync(
			() -> this.cvRepository.countFreshnessBuckets(tenantObjectId), dashboardExecutor);
		var duplicateStats = CompletableFuture.supplyAsync(
			() -> this.cvRepository.duplicateStats(tenantObjectId), dashboardExecutor);

		totalActive.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> 0L);
		flagCounts.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> List.of());
		freshnessBuckets.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> Map.of());
		duplicateStats.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
			.exceptionally(ex -> new CVDuplicatesData.DuplicateStats(0, 0));

		CompletableFuture.allOf(totalActive, flagCounts, freshnessBuckets, duplicateStats).join();

		long totalCVs = totalActive.join();
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

		Map<String, Long> flags = toFlagMap(flagCounts.join());
		Map<String, Long> buckets = normalizeFreshnessBuckets(freshnessBuckets.join(), totalCVs);
		var duplicates = duplicateStats.join();

		var completenessDim = completeness(flags, totalCVs);
		var freshnessDim = freshness(buckets, totalCVs);
		var uniquenessDim = uniqueness(duplicates, totalCVs);
		var confidenceDim = parseConfidence(flags, totalCVs);

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
			.issues(buildIssues(flags, buckets, duplicates, tenantId))
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

	private DimensionScore completeness(Map<String, Long> flags, long total) {
		var metrics = new ArrayList<Metric>();
		double weightedSum = 0;
		int weightTotal = 0;

		for (var field : COMPLETENESS_FIELDS) {
			long present = total - flagCount(flags, field.flag());
			double pct = percentage(present, total);
			metrics.add(new Metric(field.metricName(), present, pct));
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

	private DimensionScore uniqueness(CVDuplicatesData.DuplicateStats duplicates, long total) {
		long excess = Math.min(total, duplicates.excessCount());
		int score = (int) Math.round(100.0 * (total - excess) / total);
		var metrics = List.of(
			new Metric("duplicateGroups", duplicates.groupCount(), 0.0),
			new Metric("duplicateCVs", excess, percentage(excess, total))
		);
		return new DimensionScore(score, metrics);
	}

	private DimensionScore parseConfidence(Map<String, Long> flags, long total) {
		long missing = flagCount(flags, NO_AI_ANALYSIS);
		long low = flagCount(flags, LOW_AI_CONFIDENCE);
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

	// -------------------------------------------------------------------------
	// Bulk actions + issue lifecycle
	// -------------------------------------------------------------------------

	public LibraryQualityReport.ActionResult performAction(String tenantId, LibraryQualityReport.ActionRequest request) throws QorvaException {
		var tenantObjectId = new ObjectId(tenantId);
		var action = request.action() != null ? request.action().toUpperCase(Locale.ROOT) : "";
		var ids = toObjectIds(request.cvIds());

		long modified = switch (action) {
			case ACTION_ARCHIVE -> {
				if (request.issueKey() != null) {
					var issueKey = parseIssueKey(request.issueKey());
					if (issueKey == QualityIssueKeyEnum.DUPLICATES) {
						throw badRequest("Duplicates cannot be bulk-archived — resolve them individually");
					}
					yield this.cvRepository.bulkSetArchived(tenantObjectId, issueKey, null, true);
				}
				requireIds(ids);
				yield this.cvRepository.bulkSetArchived(tenantObjectId, null, ids, true);
			}
			case ACTION_UNARCHIVE -> {
				requireIds(ids);
				yield this.cvRepository.bulkSetArchived(tenantObjectId, null, ids, false);
			}
			case ACTION_CONFIRM_CURRENT -> {
				requireIds(ids);
				if (ids.size() > MAX_CONFIRM_CURRENT_PER_CALL) {
					throw badRequest("Confirm-current is limited to " + MAX_CONFIRM_CURRENT_PER_CALL
						+ " resumes per call — verification must reflect actual review");
				}
				yield this.cvRepository.bulkConfirmCurrent(tenantObjectId, ids);
			}
			default -> throw badRequest("Unknown action: " + request.action());
		};

		this.cacheEvictor.evict(tenantId);
		log.info("Library quality action {} for tenant={}: {} CVs modified", action, tenantId, modified);
		return new LibraryQualityReport.ActionResult(action, modified);
	}

	public void dismissIssue(String tenantId, String issueKey, String dismissedBy) throws QorvaException {
		var key = parseIssueKey(issueKey);
		if (this.issueStateRepository.findByTenantIdAndIssueKey(tenantId, key.name()).isEmpty()) {
			this.issueStateRepository.save(QualityIssueState.builder()
				.tenantId(tenantId)
				.issueKey(key.name())
				.dismissedBy(dismissedBy)
				.dismissedAt(Instant.now())
				.build());
		}
		this.cacheEvictor.evict(tenantId);
	}

	public void reopenIssue(String tenantId, String issueKey) throws QorvaException {
		this.issueStateRepository.deleteByTenantIdAndIssueKey(tenantId, parseIssueKey(issueKey).name());
		this.cacheEvictor.evict(tenantId);
	}

	private QualityIssueKeyEnum parseIssueKey(String issueKey) throws QorvaException {
		try {
			return QualityIssueKeyEnum.valueOf(issueKey);
		} catch (IllegalArgumentException | NullPointerException e) {
			throw badRequest("Unknown issue key: " + issueKey);
		}
	}

	private void requireIds(List<ObjectId> ids) throws QorvaException {
		if (ids.isEmpty()) {
			throw badRequest("cvIds must not be empty for this action");
		}
	}

	private List<ObjectId> toObjectIds(List<String> ids) throws QorvaException {
		if (ids == null) return List.of();
		try {
			return ids.stream().map(ObjectId::new).toList();
		} catch (IllegalArgumentException e) {
			throw badRequest("Invalid CV id in request");
		}
	}

	private QorvaException badRequest(String message) {
		return new QorvaException(message, HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
	}

	// -------------------------------------------------------------------------
	// Issues
	// -------------------------------------------------------------------------

	private List<QualityIssue> buildIssues(
		Map<String, Long> flags,
		Map<String, Long> buckets,
		CVDuplicatesData.DuplicateStats duplicates,
		String tenantId
	) {
		var counts = new LinkedHashMap<QualityIssueKeyEnum, Long>();
		counts.put(QualityIssueKeyEnum.MISSING_CONTACT, flagCount(flags, MISSING_CONTACT));
		counts.put(QualityIssueKeyEnum.MISSING_EMAIL, flagCount(flags, MISSING_EMAIL));
		counts.put(QualityIssueKeyEnum.MISSING_PHONE, flagCount(flags, MISSING_PHONE));
		counts.put(QualityIssueKeyEnum.DUPLICATES, duplicates.groupCount());
		counts.put(QualityIssueKeyEnum.OUTDATED, buckets.get(BUCKET_OUTDATED));
		counts.put(QualityIssueKeyEnum.LOW_PARSE_CONFIDENCE,
			flagCount(flags, NO_AI_ANALYSIS) + flagCount(flags, LOW_AI_CONFIDENCE));
		counts.put(QualityIssueKeyEnum.NO_WORK_EXPERIENCE, flagCount(flags, NO_WORK_EXPERIENCE));
		counts.put(QualityIssueKeyEnum.NO_SKILLS, flagCount(flags, NO_SKILLS));
		counts.put(QualityIssueKeyEnum.MISSING_SUMMARY, flagCount(flags, MISSING_SUMMARY));
		counts.put(QualityIssueKeyEnum.UNKNOWN_FRESHNESS, buckets.get(BUCKET_UNKNOWN));

		var dismissedKeys = this.issueStateRepository.findByTenantId(tenantId).stream()
			.map(QualityIssueState::getIssueKey)
			.collect(java.util.stream.Collectors.toSet());

		return counts.entrySet().stream()
			.filter(e -> e.getValue() > 0)
			.map(e -> new QualityIssue(e.getKey().name(), severityOf(e.getKey()), e.getValue(),
				dismissedKeys.contains(e.getKey().name())))
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

	private Map<String, Long> toFlagMap(List<Metric> raw) {
		var map = new LinkedHashMap<String, Long>();
		if (raw != null) {
			raw.forEach(m -> map.put(m.name(), m.count()));
		}
		return map;
	}

	private long flagCount(Map<String, Long> flags, QualityFlagEnum flag) {
		return flags.getOrDefault(flag.name(), 0L);
	}

	private Map<String, Long> normalizeFreshnessBuckets(Map<String, Long> raw, long total) {
		var buckets = new LinkedHashMap<String, Long>();
		buckets.put(BUCKET_UP_TO_DATE, raw.getOrDefault(BUCKET_UP_TO_DATE, 0L));
		buckets.put(BUCKET_REVIEW_SUGGESTED, raw.getOrDefault(BUCKET_REVIEW_SUGGESTED, 0L));
		buckets.put(BUCKET_OUTDATED, raw.getOrDefault(BUCKET_OUTDATED, 0L));
		buckets.put(BUCKET_UNKNOWN, raw.getOrDefault(BUCKET_UNKNOWN, 0L));
		// Defensive: if the counts timed out, treat everything as unknown rather than fresh.
		long counted = buckets.values().stream().mapToLong(Long::longValue).sum();
		if (counted < total) {
			buckets.put(BUCKET_UNKNOWN, buckets.get(BUCKET_UNKNOWN) + (total - counted));
		}
		return buckets;
	}

	private double percentage(long count, long total) {
		if (total == 0) return 0.0;
		return Math.round((double) count / total * 1000.0) / 10.0;
	}
}
