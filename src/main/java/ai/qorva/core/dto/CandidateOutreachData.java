package ai.qorva.core.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/** API shapes for candidate outreach ({@code /candidate-outreach}). */
public final class CandidateOutreachData {

	private CandidateOutreachData() {}

	/** Recruiter's mailbox state, as the composer needs it to choose its primary action. */
	public enum MailboxState { NONE, MICROSOFT, REAUTH_REQUIRED }

	/**
	 * Everything the composer needs on open, in one call: who the candidate is, whether they may be
	 * contacted, how the recruiter can send, and what was sent before.
	 */
	public record ContextResponse(
		String candidateName,
		String email,
		boolean suppressed,
		MailboxState mailbox,
		String mailboxAddress,
		List<CandidateOutreachDTO> history) {}

	@Getter
	@Setter
	@NoArgsConstructor
	public static class DraftRequest {

		@NotBlank
		private String cvId;

		private String jobPostId;
		private String matchingReportId;

		/** {@link ai.qorva.core.enums.OutreachIntentEnum} name. */
		@NotBlank
		private String intent;

		/** Free text such as "formal", "friendly", "short"; empty → professional and warm. */
		private String tone;

		/** Output language code (e.g. "fr"); defaults to the Accept-Language header. */
		private String language;

		@Size(max = 1000)
		private String instructions;
	}

	/** LLM output shape — kept as a mutable bean for the structured-output converter. */
	@Getter
	@Setter
	@NoArgsConstructor
	public static class Draft {
		private String subject;
		private String body;
	}

	public record DraftResponse(String subject, String body) {}

	/** The composer opened the recruiter's own client with this message; Qorva only records the hand-off. */
	@Getter
	@Setter
	@NoArgsConstructor
	public static class ExternalRequest {

		@NotBlank
		private String cvId;

		private String jobPostId;
		private String matchingReportId;

		/** {@link ai.qorva.core.enums.OutreachViaEnum} hand-off name: GMAIL, OUTLOOK_WEB or MAILTO. */
		@NotBlank
		private String via;

		@NotBlank
		@Email
		private String to;

		@Size(max = 200)
		private String subject;

		@Size(max = 8000)
		private String body;
	}

	/** Send through the recruiter's connected mailbox. */
	@Getter
	@Setter
	@NoArgsConstructor
	public static class SendRequest {

		@NotBlank
		private String cvId;

		private String jobPostId;
		private String matchingReportId;

		@NotBlank
		@Email
		private String to;

		@NotBlank
		@Size(max = 200)
		private String subject;

		@NotBlank
		@Size(max = 8000)
		private String body;
	}

	public record SendResponse(String to, Instant sentAt, String providerWebLink, CandidateOutreachDTO entry) {}
}
