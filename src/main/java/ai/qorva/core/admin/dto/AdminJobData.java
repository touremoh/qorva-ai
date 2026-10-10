package ai.qorva.core.admin.dto;

import ai.qorva.core.dao.entity.BackgroundJob;

import java.time.Instant;
import java.util.List;

/** Background jobs across tenants. */
public final class AdminJobData {

	private AdminJobData() {
	}

	public record JobRow(String id, String tenantId, String tenantName, String type, String status, long total, long processed,
	                     long succeeded, long failed, long skipped, String failureReason, String createdBy, Instant createdAt,
	                     Instant startedAt, Instant finishedAt, Instant leaseExpiresAt, boolean stuck) {

		public static JobRow from(BackgroundJob j, String tenantName) {
			return new JobRow(j.getId(), j.getTenantId(), tenantName, j.getType(), j.getStatus(), j.getTotal(), j.getProcessed(),
				j.getSucceeded(), j.getFailed(), j.getSkipped(), j.getFailureReason(), j.getCreatedBy(), j.getCreatedAt(),
				j.getStartedAt(), j.getFinishedAt(), j.getLeaseExpiresAt(), isStuck(j));
		}
	}

	public record JobDetail(String id, String tenantId, String tenantName, String type, String status, long total, long processed,
	                        long succeeded, long failed, long skipped, String failureReason, String createdBy, Instant createdAt,
	                        Instant startedAt, Instant finishedAt, Instant leaseExpiresAt, boolean stuck,
	                        List<String> errorSamples, List<String> jobIds, Integer topN, String trigger, String connectionId,
	                        String language, String issueKey) {

		public static JobDetail from(BackgroundJob j, String tenantName) {
			return new JobDetail(j.getId(), j.getTenantId(), tenantName, j.getType(), j.getStatus(), j.getTotal(), j.getProcessed(),
				j.getSucceeded(), j.getFailed(), j.getSkipped(), j.getFailureReason(), j.getCreatedBy(), j.getCreatedAt(),
				j.getStartedAt(), j.getFinishedAt(), j.getLeaseExpiresAt(), isStuck(j),
				j.getErrorSamples() != null ? j.getErrorSamples() : List.of(), j.getJobIds() != null ? j.getJobIds() : List.of(),
				j.getTopN(), j.getTrigger(), j.getConnectionId(), j.getLanguage(), j.getIssueKey());
		}
	}

	/** RUNNING with a lease nobody renewed: the worker died; another instance will reclaim it. */
	public static boolean isStuck(BackgroundJob j) {
		return BackgroundJob.STATUS_RUNNING.equals(j.getStatus()) && j.getLeaseExpiresAt() != null
			&& j.getLeaseExpiresAt().isBefore(Instant.now());
	}
}
