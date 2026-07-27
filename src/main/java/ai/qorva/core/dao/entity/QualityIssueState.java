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

/**
 * Per-tenant triage state for a Library Quality issue type. At scale some findings are
 * consciously accepted risk — a dismissed issue stays computed (scores remain honest)
 * but is presented collapsed and excluded from the sidebar badge count.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "quality_issue_states")
public class QualityIssueState {

	@Id
	private String id;

	@Field(targetType = FieldType.OBJECT_ID)
	private String tenantId;

	private String issueKey;

	private String dismissedBy;

	private Instant dismissedAt;
}
