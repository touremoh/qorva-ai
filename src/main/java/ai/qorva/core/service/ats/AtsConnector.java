package ai.qorva.core.service.ats;

import ai.qorva.core.enums.AtsProviderEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.ats.AtsModels.AtsCandidate;
import ai.qorva.core.service.ats.AtsModels.AtsFile;
import ai.qorva.core.service.ats.AtsModels.AtsJob;
import ai.qorva.core.service.ats.AtsModels.AtsPage;
import ai.qorva.core.service.ats.AtsModels.AtsWebhookEvent;
import ai.qorva.core.service.ats.AtsModels.MatchWriteBack;
import org.springframework.http.HttpHeaders;

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
}
