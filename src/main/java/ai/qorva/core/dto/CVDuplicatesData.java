package ai.qorva.core.dto;

import java.time.Instant;
import java.util.List;

public class CVDuplicatesData {

	private CVDuplicatesData() {}

	public record CVSummary(String cvId, String name, String email, String phone, Instant createdAt) {}

	/** Raw output from the repository aggregation — one entry per duplicate group key. */
	public record DuplicateAggResult(String matchValue, int count, List<CVSummary> cvs) {}

	/** Cheap counts for the quality report: number of groups and of excess copies (Σ count−1). */
	public record DuplicateStats(long groupCount, long excessCount) {}

	/** API-facing group: same as DuplicateAggResult but enriched with the matchType. */
	public record DuplicateGroup(String matchType, String matchValue, int count, List<CVSummary> cvs) {}

	public record DuplicatesPage(
		List<DuplicateGroup> content,
		int pageNumber,
		int pageSize,
		long totalElements,
		int totalPages,
		boolean hasNext
	) {}
}
