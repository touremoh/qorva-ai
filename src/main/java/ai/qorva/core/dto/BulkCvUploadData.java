package ai.qorva.core.dto;

/** API shapes for asynchronous bulk CV imports ({@code /cvs/bulk-uploads}). */
public final class BulkCvUploadData {

	private BulkCvUploadData() {}

	/** Draft job created; the client stages files in chunks, then starts it. */
	public record CreateResponse(String jobId, int maxFiles) {}

	/** State after one staging chunk. */
	public record StageResponse(String jobId, int stagedCount, int maxFiles) {}

	/**
	 * Job accepted for processing. {@code willProcess} is the number of files the plan's
	 * remaining screening-action quota covers right now (equal to {@code total} when the
	 * whole job fits, or when the plan has no limit configured).
	 */
	public record StartResponse(BackgroundJobData.JobView job, long willProcess) {}
}
