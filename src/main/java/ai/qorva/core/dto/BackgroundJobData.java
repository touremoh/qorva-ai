package ai.qorva.core.dto;

import ai.qorva.core.dao.entity.BackgroundJob;

import java.time.Instant;
import java.util.List;

/** API shapes for asynchronous bulk operations. */
public final class BackgroundJobData {

	private BackgroundJobData() {}

	/** Submit request; {@code dryRun: true} returns an estimate without creating a job. */
	public record SubmitRequest(String type, String issueKey, boolean dryRun, String language) {}

	/** Pre-flight cost estimate shown before the user confirms an expensive job. */
	public record Estimate(
		long affectedCount,
		long skippedNoRawText,
		long estimatedActions,
		Integer remainingQuota      // null when the plan has no limit configured
	) {}

	public record JobView(
		String id,
		String type,
		String issueKey,
		String status,
		long total,
		long processed,
		long succeeded,
		long failed,
		long skipped,
		String failureReason,
		Instant createdAt,
		Instant startedAt,
		Instant finishedAt
	) {
		public static JobView from(BackgroundJob job) {
			return new JobView(job.getId(), job.getType(), job.getIssueKey(), job.getStatus(),
				job.getTotal(), job.getProcessed(), job.getSucceeded(), job.getFailed(), job.getSkipped(),
				job.getFailureReason(), job.getCreatedAt(), job.getStartedAt(), job.getFinishedAt());
		}
	}

	public record SubmitResponse(Estimate estimate, JobView job) {}

	public record JobList(List<JobView> jobs) {}
}
