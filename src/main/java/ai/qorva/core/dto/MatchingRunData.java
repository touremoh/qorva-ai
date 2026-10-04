package ai.qorva.core.dto;

import ai.qorva.core.dao.entity.BackgroundJob;

import java.time.Instant;
import java.util.List;

/** API shapes for matching runs: what the run dialog sends, the cost it shows, and the run it follows. */
public final class MatchingRunData {

	private MatchingRunData() {}

	/** {@code topN} null → the plan default. */
	public record Request(List<String> jobIds, Integer topN) {}

	public record JobEstimate(String jobId, String title, int candidates, int newReports, int reusedReports, boolean indexing) {}

	/**
	 * The cost of a run before it starts. {@code estimatedActions} is what it may charge (new reports only);
	 * {@code remainingActions} is null when the plan has no limit. {@code allowedTopN}/{@code defaultTopN}
	 * are the plan's choices, so the dialog never offers what the run would refuse.
	 */
	public record Estimate(
		int topN,
		List<Integer> allowedTopN,
		int defaultTopN,
		int candidates,
		int newReports,
		int reusedReports,
		int estimatedActions,
		Integer remainingActions,
		List<JobEstimate> jobs
	) {}

	/** Top N choices of the plan alone, for the dialog before any job is picked. */
	public record Options(List<Integer> allowedTopN, int defaultTopN, int maxTopN) {}

	public record RunView(
		String id,
		String status,
		List<String> jobIds,
		Integer topN,
		long total,
		long processed,
		long generated,
		long reused,
		long failed,
		long skippedJobs,
		String failureReason,
		Instant createdAt,
		Instant startedAt,
		Instant finishedAt
	) {
		public static RunView from(BackgroundJob job) {
			return new RunView(job.getId(), job.getStatus(), job.getJobIds(), job.getTopN(), job.getTotal(),
				job.getProcessed(), job.getSucceeded(), job.getReused(), job.getFailed(), job.getSkipped(),
				job.getFailureReason(), job.getCreatedAt(), job.getStartedAt(), job.getFinishedAt());
		}
	}

	public record SubmitResponse(Estimate estimate, RunView run) {}

	public record RunList(List<RunView> runs) {}

	public record DeleteOutdatedResponse(long deleted) {}
}
