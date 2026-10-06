package ai.qorva.core.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A support request a user confirmed from Qorva Help. Stored first, then emailed to the support mailbox;
 * a failed email is retried by {@code SupportTicketEmailScheduler} ({@code emailStatus}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "support_tickets")
public class SupportTicket implements QorvaEntity {

	public static final String STATUS_OPEN = "OPEN";
	public static final String EMAIL_PENDING = "PENDING";
	public static final String EMAIL_SENT = "SENT";
	public static final String EMAIL_FAILED = "FAILED";

	@Id
	private String id;

	@Field(targetType = FieldType.OBJECT_ID)
	private String tenantId;

	private String reference;
	private String userEmail;
	/** Snapshots for the support email, taken when the ticket is created. */
	private String userName;
	private String companyName;
	private String subject;
	private String description;
	private String page;
	private String language;
	/** The help conversation at the time, when the user chose to include it. */
	private List<HelpConversation.Turn> transcript = new ArrayList<>();
	private String status;
	private String emailStatus;
	private int emailAttempts;
	private Instant emailedAt;
	private Instant createdAt;
}
