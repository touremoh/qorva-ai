package ai.qorva.core.service.ats;

import java.time.Instant;
import java.util.List;

/**
 * Normalized shapes exchanged between connectors and the sync engine. Deliberately
 * thin: Qorva's candidate profile comes from LLM extraction of the resume file, not
 * from mapping every ATS field — a connector only has to say who exists, what changed,
 * and where the resume bytes are.
 */
public final class AtsModels {

	private AtsModels() {}

	/** One page of a provider stream. nextCursor == null means the stream is drained. */
	public record AtsPage<T>(List<T> items, String nextCursor) {}

	public record AtsCandidate(
		String externalId,
		String externalApplicationId,
		String name,
		String email,
		Instant updatedAt,
		String externalUrl,
		/* Provider-native pointer to the primary resume; null when the candidate has no file. */
		String resumeHandle,
		String resumeFilename
	) {}

	public record AtsJob(
		String externalId,
		String title,
		String description,
		boolean open,
		Instant updatedAt,
		String externalUrl
	) {}

	public record AtsFile(byte[] bytes, String filename, String contentType) {}

	public record MatchWriteBack(
		String externalCandidateId,
		String externalApplicationId,
		String jobTitle,
		Double score,
		String headline,
		String reportUrl
	) {}

	/** Parsed inbound webhook: enough to decide whether a targeted delta sync is worth enqueueing. */
	public record AtsWebhookEvent(String type, String externalId) {}

	/**
	 * What a provider handed back when Qorva registered its webhooks.
	 *
	 * <p>externalIds are the subscriptions to delete when the connection goes away — without
	 * them a disconnect would leave dead endpoints configured in the customer's ATS.</p>
	 *
	 * <p>signingSecret is set only when the provider insists on choosing the HMAC key itself
	 * (Lever). Providers that accept ours (Ashby) or sign with a credential we already hold
	 * (Workable) leave it null, and the caller keeps what it had.</p>
	 */
	public record WebhookRegistration(List<String> externalIds, String signingSecret) {
		public static WebhookRegistration of(List<String> externalIds) {
			return new WebhookRegistration(externalIds, null);
		}
	}
}
