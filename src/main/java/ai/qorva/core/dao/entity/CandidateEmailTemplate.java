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
 * A recruiter-authored invitation message for candidate-update campaigns. Plain text
 * with {{candidate_name}}/{{company_name}} placeholders — never HTML; the shell
 * (button, unsubscribe footer) stays application-owned. Campaigns snapshot subject/body
 * onto the job at submit time, so editing or deleting a template never affects a
 * running campaign.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "candidate_email_templates")
public class CandidateEmailTemplate {

	@Id
	private String id;

	@Field(targetType = FieldType.OBJECT_ID)
	private String tenantId;

	/** Display name shown in the campaign picker; unique per tenant. */
	private String name;

	private String subject;

	private String bodyText;

	private String createdBy;
	private Instant createdAt;
	private Instant updatedAt;
}
