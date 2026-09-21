package ai.qorva.core.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

/**
 * One recruiter → candidate contact: an email sent through the recruiter's connected mailbox,
 * or a message handed off to their own mail client. This is the "who contacted whom, when" trail
 * shown in the composer; the recruiter's own Sent folder stays the source of truth for delivery.
 *
 * <p>Own collection for the same reason as {@link Note}: nothing may be written to {@code cvs}
 * as a side effect of contacting a candidate (that re-flags every open job for screening).</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "candidate_outreach")
public class CandidateOutreach implements QorvaEntity {

	public static final String CHANNEL_EMAIL = "EMAIL";

	public static final String STATUS_SENT = "SENT";
	public static final String STATUS_EXTERNAL_OPENED = "EXTERNAL_OPENED";
	public static final String STATUS_FAILED = "FAILED";

	@Id
	private String id;

	@Field(targetType = FieldType.OBJECT_ID)
	private String tenantId;

	/** Id of the CV, stored as a string like {@code candidateInfo.candidateId}. */
	private String cvId;
	private String jobPostId;
	private String matchingReportId;

	/** {@link #CHANNEL_EMAIL} — the only channel today. */
	private String channel;

	/** {@link #STATUS_SENT} | {@link #STATUS_EXTERNAL_OPENED} | {@link #STATUS_FAILED}. */
	private String status;

	private String to;
	private String subject;
	private String body;

	/** {@link ai.qorva.core.enums.OutreachViaEnum} name — which client or connection carried it. */
	private String via;

	/** Set when sent through a connected mailbox; lets the history row open the message in Outlook web. */
	private String providerMessageId;
	private String providerThreadId;
	private String providerWebLink;

	/** Why a connected send failed (provider error, truncated). */
	private String error;

	/** Display name captured at creation so readers never need VIEW_USERS to see who wrote it. */
	private String senderName;

	@CreatedBy
	private String senderEmail;

	@CreatedDate
	private Instant createdAt;
}
