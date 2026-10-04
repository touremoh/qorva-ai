package ai.qorva.core.dto.common;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/** One move of a candidate's status on a job, kept on the report (last 20) for the history line and metrics. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportStatusChange implements Serializable {

	/** {@code ApplicationStatusEnum} names. */
	private String from;
	private String status;

	/** User id, or {@code copilot:<runId>} / {@code ats:<provider>} when no person made the change. */
	private String by;

	/** Display name of the user at the time, so the history reads without a lookup. */
	private String byName;

	/** {@code ReportStatusChannel} name: how the change was made. */
	private String via;

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
	private Instant at;
}
