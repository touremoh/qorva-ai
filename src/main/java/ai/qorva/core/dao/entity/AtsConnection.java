package ai.qorva.core.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;
import java.util.List;

/**
 * A tenant's link to one external ATS. Credentials are stored only as an AES-GCM
 * encrypted JSON blob (see CredentialsCipher) — the plaintext never touches Mongo,
 * logs, or DTOs. Webhook calls authenticate against webhookSecret (HMAC where the
 * provider signs payloads, shared-secret token otherwise).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "ats_connections")
public class AtsConnection implements QorvaEntity {

	public static final String STATUS_CONNECTED = "CONNECTED";
	public static final String STATUS_AUTH_ERROR = "AUTH_ERROR";
	public static final String STATUS_DISABLED = "DISABLED";

	@Id
	private String id;

	@Field(targetType = FieldType.OBJECT_ID)
	private String tenantId;

	/** AtsProviderEnum value, e.g. "greenhouse". One connection per provider per tenant. */
	private String provider;

	private String displayName;
	private String status;

	/** Encrypted AtsCredentials JSON (AES-GCM, base64). */
	private String encryptedCredentials;

	private Settings settings;
	private SyncState syncState;

	/** Random per-connection secret used to authenticate inbound webhooks. */
	private String webhookSecret;

	/** What Qorva registered with the provider, when webhooks are created automatically. */
	private WebhookState webhookState;

	@Getter
	@Setter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Settings {
		/** Scheduler enqueues periodic delta syncs when true. */
		private Boolean autoImport;
		/** Import open jobs from the ATS as Qorva job posts. */
		private Boolean importJobs;
		/** Push matching-report scores back to the ATS as candidate notes. */
		private Boolean writeBackScores;
		/**
		 * First sync only: candidates whose count exceeds the tenant's remaining screening
		 * quota (or the configured guard) require an explicit confirmation with this flag.
		 */
		private Boolean initialSyncConfirmed;
	}

	/**
	 * Bookkeeping for automatically registered webhooks. Holds no secrets — the signing key a
	 * provider hands back lives in the encrypted credentials blob.
	 */
	@Getter
	@Setter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class WebhookState {
		/** Provider-side subscription ids, needed to remove them again on disconnect. */
		private List<String> externalIds;
		/**
		 * The callback URL these subscriptions point at. Compared against the current one so a
		 * changed ATS_PUBLIC_BASE_URL re-registers instead of silently going deaf.
		 */
		private String registeredUrl;
		private Instant registeredAt;
		/** Why the last registration attempt failed; null once one succeeds. */
		private String lastError;
	}

	@Getter
	@Setter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SyncState {
		/** Provider-native cursor for the candidates stream (timestamp or paging token). */
		private String candidatesCursor;
		private String jobsCursor;
		private Instant lastSyncAt;
		private String lastSyncError;
	}

	@CreatedDate
	private Instant createdAt;

	@LastModifiedDate
	private Instant lastUpdatedAt;

	@CreatedBy
	private String createdBy;

	@LastModifiedBy
	private String lastUpdatedBy;
}
