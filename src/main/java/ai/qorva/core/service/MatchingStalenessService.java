package ai.qorva.core.service;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.repository.SimilaritySearchRepository;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.enums.MatchingStaleReasonEnum;
import ai.qorva.core.mapper.CVMapper;
import ai.qorva.core.security.TenantScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Smart flagging: a new or changed CV flags only the jobs whose results it would actually change, instead
 * of every open job. CVs are queued on creation / matching-relevant edit ({@code matchCheckPending}); once
 * Atlas has (re)embedded them, each is compared with every matched open job of its tenant:
 * <ul>
 *   <li>a CV that already has a current report on the job, computed from different CV content → the job is
 *   flagged {@code CANDIDATE_CHANGED};</li>
 *   <li>otherwise, an eligible CV whose similarity reaches the job's cutoff (its N-th result at the last run)
 *   → {@code NEW_CANDIDATES}, and its id is remembered for the badge.</li>
 * </ul>
 * Similarity is computed here exactly as {@code $vectorSearch} scores cosine, {@code (1 + cos) / 2}, so it is
 * comparable with the stored cutoff. Flags are idempotent, so two instances sweeping the same CV is harmless.
 * A CV whose embedding never arrives falls back to flagging every open job, as before.
 */
@Slf4j
@Service
public class MatchingStalenessService {

	/** Bounds one tick, so a large import cannot keep the scheduler thread busy indefinitely. */
	static final int MAX_BATCHES_PER_TICK = 20;

	private final MongoTemplate mongoTemplate;
	private final CVMapper cvMapper;
	private final JobPostService jobPostService;
	private final MatchingReportService matchingReportService;
	private final Duration grace;
	private final Duration embeddingTimeout;
	private final int batchSize;
	private final TenantAccess tenantAccess;

	public MatchingStalenessService(MongoTemplate mongoTemplate, CVMapper cvMapper, JobPostService jobPostService,
	                                MatchingReportService matchingReportService, TenantAccess tenantAccess,
	                                @Value("${qorva.matching.staleness.grace-seconds:30}") long graceSeconds,
	                                @Value("${qorva.matching.staleness.embedding-timeout-minutes:5}") long embeddingTimeoutMinutes,
	                                @Value("${qorva.matching.staleness.batch-size:200}") int batchSize) {
		this.mongoTemplate = mongoTemplate;
		this.cvMapper = cvMapper;
		this.jobPostService = jobPostService;
		this.matchingReportService = matchingReportService;
		this.grace = Duration.ofSeconds(graceSeconds);
		this.embeddingTimeout = Duration.ofMinutes(embeddingTimeoutMinutes);
		this.batchSize = batchSize;
		this.tenantAccess = tenantAccess;
	}

	public record Outcome(int checked, int jobsFlagged, int fallbacks) {
	}

	/** One pass: every CV that is ready to compare, then the ones whose embedding never came. */
	public Outcome sweep(Instant now) {
		return TenantScope.callAsSystem("matching staleness sweep", () -> {
			int checked = 0;
			int flagged = 0;
			for (int i = 0; i < MAX_BATCHES_PER_TICK; i++) {
				var batch = pending(now.minus(grace), true);
				if (batch.isEmpty()) break;
				for (var entry : byTenant(batch).entrySet()) {
					flagged += TenantScope.callAs(entry.getKey(), () -> checkTenant(entry.getKey(), entry.getValue()));
				}
				checked += batch.size();
				if (batch.size() < batchSize) break;
			}
			int fallbacks = 0;
			var stalled = pending(now.minus(embeddingTimeout), false);
			for (var entry : byTenant(stalled).entrySet()) {
				log.warn("Matching staleness: {} CV(s) of tenant {} still have no embedding after {} — flagging every open job",
					entry.getValue().size(), entry.getKey(), embeddingTimeout);
				TenantScope.runAs(entry.getKey(), () -> jobPostService.markOpenJobPostsAsNeedingReports(entry.getKey()));
				entry.getValue().forEach(this::clearPending);
				fallbacks += entry.getValue().size();
			}
			return new Outcome(checked, flagged, fallbacks);
		});
	}

	/** Compares the tenant's ready CVs with its matched open jobs; returns how many job flags it raised. */
	int checkTenant(String tenantId, List<CV> cvs) {
		var jobs = jobPostService.findMatchedOpenJobs(tenantId);
		int flagged = 0;
		if (!jobs.isEmpty()) {
			var cvIds = cvs.stream().map(CV::getId).toList();
			var jobIds = jobs.stream().map(JobPostDTO::getId).toList();
			var reports = new HashMap<String, String>();
			matchingReportService.currentReportsOf(tenantId, cvIds, jobIds)
				.forEach(r -> reports.put(r.jobPostId() + ":" + r.candidateId(), String.valueOf(r.cvFingerprint())));
			for (var entity : cvs) {
				var cv = cvMapper.map(entity);
				var cvFingerprint = MatchingFingerprint.cv(cv);
				for (var job : jobs) {
					var reported = reports.get(job.getId() + ":" + cv.getId());
					if (reported != null) {
						if (!reported.equals(cvFingerprint)) {
							jobPostService.flagStale(tenantId, job.getId(), MatchingStaleReasonEnum.CANDIDATE_CHANGED, null);
							flagged++;
						}
					} else if (wouldEnterTopN(job, cv)) {
						jobPostService.flagStale(tenantId, job.getId(), MatchingStaleReasonEnum.NEW_CANDIDATES, cv.getId());
						flagged++;
					}
				}
			}
		}
		cvs.forEach(this::clearPending);
		return flagged;
	}

	/** Whether the candidate passes the job's filters and is at least as similar as the job's current N-th result. */
	static boolean wouldEnterTopN(JobPostDTO job, CVDTO cv) {
		if (!CvEligibility.eligible(cv, job.getScoringRules())) {
			return false;
		}
		var score = similarity(job.getEmbedding(), cv.getEmbedding());
		if (score == null) {
			return false;
		}
		double cutoff = job.getMatchingCutoffScore() != null
			? job.getMatchingCutoffScore()
			: SimilaritySearchRepository.MIN_MATCH_SCORE;
		return score >= Math.max(cutoff, SimilaritySearchRepository.MIN_MATCH_SCORE);
	}

	/** Cosine similarity mapped to 0..1 the way Atlas scores a cosine index; null when the vectors don't compare. */
	static Double similarity(float[] a, float[] b) {
		if (a == null || b == null || a.length == 0 || a.length != b.length) {
			return null;
		}
		double dot = 0, normA = 0, normB = 0;
		for (int i = 0; i < a.length; i++) {
			dot += (double) a[i] * b[i];
			normA += (double) a[i] * a[i];
			normB += (double) b[i] * b[i];
		}
		if (normA == 0 || normB == 0) {
			return null;
		}
		return (1 + dot / (Math.sqrt(normA) * Math.sqrt(normB))) / 2;
	}

	/** Pending CVs marked before {@code before}: with an embedding when {@code embedded}, without one otherwise. */
	private List<CV> pending(Instant before, boolean embedded) {
		var criteria = Criteria.where("matchCheckPending").is(true).and("matchCheckPendingSince").lte(before);
		criteria = embedded ? criteria.and("embedding").ne(null) : criteria.and("embedding").is(null);
		// A suspended, deleted or expired company's CVs stay queued until it is usable again.
		var query = Query.query(new Criteria().andOperator(criteria, tenantAccess.usableTenantsOnly("tenantId"))).with(Sort.by("matchCheckPendingSince")).limit(batchSize);
		if (!embedded) {
			query.fields().include("_id", "tenantId", "matchCheckPendingSince");
		}
		return mongoTemplate.find(query, CV.class);
	}

	private static Map<String, List<CV>> byTenant(List<CV> cvs) {
		return cvs.stream().collect(Collectors.groupingBy(CV::getTenantId, LinkedHashMap::new, Collectors.toList()));
	}

	/** Only if not marked again meanwhile — an edit during the sweep must be checked on the next pass. */
	private void clearPending(CV cv) {
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(cv.getId())
				.and("matchCheckPendingSince").is(cv.getMatchCheckPendingSince())),
			new Update().unset("matchCheckPending").unset("matchCheckPendingSince"), CV.class);
	}
}
