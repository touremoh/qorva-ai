package ai.qorva.core.dto;

import ai.qorva.core.dto.common.ReportStatusChange;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;

/** The pipeline board: one column per status, each with its exact count and one page of light cards. */
public final class PipelineBoardData {

	private PipelineBoardData() {
	}

	/** A report as a card: who, for which job, how good a match, and since when in this status. */
	public record Card(
		String id,
		String jobPostId,
		String jobPostTitle,
		String candidateId,
		String candidateName,
		Double score,
		String recommendation,
		boolean outdated,
		String status,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC") Instant statusChangedAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC") Instant createdAt,
		ReportStatusChange lastMove
	) {}

	/** {@code nextCursor} is null on the last page. */
	public record Column(String status, long count, List<Card> items, String nextCursor) {}

	public record Board(List<Column> columns) {}
}
