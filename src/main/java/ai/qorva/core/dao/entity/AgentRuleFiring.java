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

/**
 * Ledger of what a rule has fired for: one row per rule and record (unique), so a re-scored report, a
 * restarted scheduler or two instances racing never start the same work twice.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "agent_rule_firings")
public class AgentRuleFiring implements QorvaEntity {

	@Id
	private String id;

	@Field(targetType = FieldType.OBJECT_ID)
	private String tenantId;

	private String ruleId;
	/** cvId (CV_ADDED), cvId:jobId (CV_SCORED), syncJobId (ATS_SYNC_FINISHED), slot:&lt;instant&gt; (SCHEDULE). */
	private String subjectKey;
	private String runId;
	private Instant firedAt;
}
