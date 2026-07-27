package ai.qorva.core.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

/** Do-not-contact list, honored before every candidate-update send. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "suppressed_emails")
public class SuppressedEmail {

	public static final String REASON_UNSUBSCRIBED = "UNSUBSCRIBED";
	public static final String REASON_BOUNCED = "BOUNCED";

	@Id
	private String id;

	@Field(targetType = FieldType.OBJECT_ID)
	private String tenantId;

	private String email;

	private String reason;

	private Instant createdAt;
}
