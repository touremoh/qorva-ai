package ai.qorva.core.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The Dashboard's pipeline card: where candidates stand now, and who moved them in the period. Built from the
 * reports' status history, so only moves made since the pipeline shipped count.
 */
public record PipelineDashboardData(
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC") Instant from,
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC") Instant to,
	String jobPostId,
	/* Current status → number of reports (outdated reports included: they are still candidates on the job). */
	Map<String, Long> currentByStatus,
	List<RecruiterPipeline> recruiters
) {
	/**
	 * One recruiter's moves in the period. {@code by} is the user id, or {@code COPILOT} for every change made by
	 * Copilot runs. Time to shortlist runs from the report's creation to its first move to Shortlisted.
	 */
	public record RecruiterPipeline(
		String by,
		String name,
		Map<String, Long> movesByStatus,
		long totalMoves,
		Double medianHoursToShortlist
	) {}
}
