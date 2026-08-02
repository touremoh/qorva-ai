package ai.qorva.core.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * Tenant-level CV library health report: one overall score plus four dimensions
 * (completeness, freshness, uniqueness, parse confidence) and an actionable issue list.
 */
@Builder
public record LibraryQualityReport(
	long totalCVs,
	Integer overallScore,                 // null when the library is empty
	DimensionScore completeness,
	DimensionScore freshness,
	DimensionScore uniqueness,
	DimensionScore parseConfidence,
	List<QualityIssue> issues
) {

	public record DimensionScore(int score, List<Metric> metrics) {}

	public record Metric(String name, long count, double percentage) {}

	public record QualityIssue(String issueKey, String severity, long count, boolean dismissed) {}

	/** Lightweight sidebar-badge payload — served from the cached report. */
	public record Summary(int openIssueCount) {}

	/** Bulk remediation request: criteria mode (issueKey) or explicit selection (cvIds). */
	public record ActionRequest(String action, String issueKey, List<String> cvIds) {}

	public record ActionResult(String action, long modifiedCount) {}

	public record IssueCV(
		String id,
		String name,
		String role,
		String email,
		String phone,
		Instant lastUpdatedAt,
		Instant contentDate
	) {}

	public record IssueCVPage(
		List<IssueCV> content,
		int pageNumber,
		int pageSize,
		long totalElements,
		int totalPages,
		boolean hasNext
	) {}
}
