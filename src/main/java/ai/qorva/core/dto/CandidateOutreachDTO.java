package ai.qorva.core.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One row of a candidate's outreach history, as shown in the composer. The body is included so a recruiter can re-read what was sent. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateOutreachDTO {
	private String id;
	private String cvId;
	private String jobPostId;
	private String matchingReportId;
	private String channel;
	private String status;
	private String to;
	private String subject;
	private String body;
	private String via;
	private String providerWebLink;
	private String error;
	private String senderName;
	private String senderEmail;

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
	private Instant createdAt;
}
