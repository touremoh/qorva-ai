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

	public record QualityIssue(String issueKey, String severity, long count) {}

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

	/** Single-pass field-presence counts (also carries the structural parse anomalies). */
	public record FieldPresenceCounts(
		long total,
		long hasName,
		long hasRole,
		long hasEmail,
		long hasPhone,
		long missingContact,
		long hasWorkExperience,
		long hasSkills,
		long hasEducation,
		long hasCareerStartYear,
		long hasLanguages,
		long hasCertifications,
		long hasSalary,
		long hasLinkedin,
		long hasSummary
	) {}

	/** Parse-confidence aggregation result. */
	public record ConfidenceCounts(
		long total,
		long missingClustering,
		long lowConfidence,
		Double avgConfidence
	) {}
}
