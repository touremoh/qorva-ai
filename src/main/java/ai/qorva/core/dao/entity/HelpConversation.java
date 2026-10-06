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
 * One Qorva Help conversation of one user. The history replayed to the model is read from here, never
 * from the request, so a client cannot put words in the assistant's mouth. Deleted by a TTL index on
 * {@code expiresAt}, pushed back on every message.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "help_conversations")
public class HelpConversation implements QorvaEntity {

	@Id
	private String id;

	@Field(targetType = FieldType.OBJECT_ID)
	private String tenantId;

	private String userEmail;
	private String language;
	private List<Turn> turns = new ArrayList<>();
	/** Hash of the help text the last answer was written from. */
	private String kbVersion;
	private Instant createdAt;
	private Instant updatedAt;
	private Instant expiresAt;

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Turn {
		private String question;
		private String answer;
		private List<String> links = new ArrayList<>();
		private String page;
		private Instant at;
	}
}
