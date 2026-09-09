package ai.qorva.core.service.ats;

import ai.qorva.core.enums.AtsProviderEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.ats.AtsModels.AtsCandidate;
import ai.qorva.core.service.ats.AtsModels.AtsFile;
import ai.qorva.core.service.ats.AtsModels.AtsJob;
import ai.qorva.core.service.ats.AtsModels.AtsPage;
import ai.qorva.core.service.ats.AtsModels.AtsWebhookEvent;
import ai.qorva.core.service.ats.AtsModels.MatchWriteBack;
import ai.qorva.core.service.ats.AtsModels.WebhookRegistration;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.Optional;

/**
 * One implementation per ATS provider. Implementations are stateless — every call
 * receives the decrypted credentials — and speak only in normalized AtsModels shapes.
 * Cursor format is provider-private: the engine stores and replays it verbatim.
 */
public interface AtsConnector {

	AtsProviderEnum provider();

	/** Cheap authenticated ping; throws QorvaException(ATS_AUTH_FAILED) on bad credentials. */
	void validate(AtsCredentials credentials) throws QorvaException;

	AtsPage<AtsJob> listJobs(AtsCredentials credentials, String cursor) throws QorvaException;

	/** Delta stream: a null cursor starts from the beginning, otherwise resumes/limits to changes. */
	AtsPage<AtsCandidate> listCandidates(AtsCredentials credentials, String cursor) throws QorvaException;

	/** Fetch the candidate's primary resume; null when the candidate has no file. */
	AtsFile downloadResume(AtsCredentials credentials, AtsCandidate candidate) throws QorvaException;

	void pushMatchResult(AtsCredentials credentials, MatchWriteBack payload) throws QorvaException;

	/**
	 * Verify and parse an inbound webhook. Empty = not authentic or not interesting;
	 * the caller answers 200 either way so probers learn nothing.
	 */
	Optional<AtsWebhookEvent> parseWebhook(HttpHeaders headers, byte[] body, String webhookSecret);

	/**
	 * True when this provider exposes an API for creating its own webhook subscriptions, so
	 * the tenant never has to configure one by hand. False keeps the manual instructions the
	 * Integrations tab shows.
	 */
	default boolean supportsWebhookRegistration() {
		return false;
	}

	/**
	 * Subscribe callbackUrl to the events this connector cares about, using secret as the
	 * signing key where the provider lets us choose one. Implementations must be safe to call
	 * again: the caller unregisters the previous ids first, but a provider that rejects a
	 * duplicate target (Workable answers 409) should treat that as success, not an error.
	 */
	default WebhookRegistration registerWebhooks(AtsCredentials credentials, String callbackUrl, String secret)
		throws QorvaException {
		throw new UnsupportedOperationException(provider() + " cannot register webhooks");
	}

	/** Best-effort removal of previously registered subscriptions; never throws. */
	default void unregisterWebhooks(AtsCredentials credentials, List<String> externalIds) {
		// Providers without registration have nothing to clean up.
	}
}
