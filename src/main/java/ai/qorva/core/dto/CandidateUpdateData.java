package ai.qorva.core.dto;

/** Public candidate self-update API shapes — deliberately minimal, never the full CV. */
public final class CandidateUpdateData {

	private CandidateUpdateData() {}

	/** Prefill for the public form: first name + current availability/salary only. */
	public record Prefill(
		String firstName,
		String availabilityStatus,
		String availableFrom,
		Integer noticePeriodDays,
		Boolean openToWork,
		String salaryCurrency,
		Integer salaryMin,
		Integer salaryMax,
		String language
	) {}

	/** Candidate-submitted update. All fields optional — only provided values are applied. */
	public record Submission(
		String availabilityStatus,
		String availableFrom,
		Integer noticePeriodDays,
		Boolean openToWork,
		String salaryCurrency,
		Integer salaryMin,
		Integer salaryMax
	) {}
}
