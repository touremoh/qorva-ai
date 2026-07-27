package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dto.CVDuplicatesData;
import ai.qorva.core.enums.QualityIssueKeyEnum;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Indexed queries backing the Library Quality feature. Every method here must stay
 * off collection scans — they run per report view and per bulk action at any library size.
 * Implementations must project out heavy fields (attachment, rawText, embedding) and
 * exclude archived CVs.
 */
public interface CVQualityRepository {

	/** Pages of CVs affected by a given issue (flag- or contentDate-indexed). */
	Page<CV> findQualityIssueCVs(ObjectId tenantId, QualityIssueKeyEnum issueKey, Pageable pageable);

	/** Non-archived CV count for the tenant. */
	long countActiveByTenantId(ObjectId tenantId);

	/** Freshness bucket counts (UP_TO_DATE / REVIEW_SUGGESTED / OUTDATED / UNKNOWN) via contentDate range counts. */
	Map<String, Long> countFreshnessBuckets(ObjectId tenantId);

	/** Duplicate group/excess counts — no group content, cheap enough for every report view. */
	CVDuplicatesData.DuplicateStats duplicateStats(ObjectId tenantId);

	/** Server-side paged duplicate groups (email + phone unified, sorted by group size). */
	CVDuplicatesData.DuplicatesPage findDuplicateGroups(ObjectId tenantId, int pageNumber, int pageSize);

	/**
	 * Ingest-time duplicate lookup: the first non-archived CV (excluding {@code excludeId})
	 * sharing the given email or phone. Two indexed point lookups — O(1) per uploaded file.
	 */
	Optional<CV> findContactMatch(ObjectId tenantId, String email, String phone, ObjectId excludeId);

	/**
	 * Bulk archive/unarchive. Criteria mode ({@code issueKey} set) archives every CV matching
	 * the issue in one indexed updateMany; id mode targets an explicit selection.
	 */
	long bulkSetArchived(ObjectId tenantId, QualityIssueKeyEnum issueKey, List<ObjectId> ids, boolean archived);

	/** Marks the given CVs as human-verified current (contentDate=now, source=VERIFIED). Ids only, by design. */
	long bulkConfirmCurrent(ObjectId tenantId, List<ObjectId> ids);

	/** Count of CVs matching an issue; optionally only those without stored rawText (not re-analyzable). */
	long countQualityIssueCVs(ObjectId tenantId, QualityIssueKeyEnum issueKey, boolean onlyMissingRawText);

	/** Materializes the ids of every CV matching an issue (projection-only; bounded by library size). */
	List<ObjectId> findQualityIssueCvIds(ObjectId tenantId, QualityIssueKeyEnum issueKey);
}
