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

/**
 * A recruiter's free-text note on a CV or a matching report. Lives in its own collection on
 * purpose: writing to {@code cvs} re-flags every open job for re-screening
 * ({@code CVService.postProcessUpdateOne}) and the whole CV document is serialised into the
 * screening prompt, so notes must never sit on the documents they annotate.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notes")
public class Note implements QorvaEntity {

	@Id
	private String id;

	@Field(targetType = FieldType.OBJECT_ID)
	private String tenantId;

	/** {@link ai.qorva.core.enums.NoteTargetTypeEnum} name. */
	private String targetType;

	/** Id of the CV or matching report, stored as a string like {@code candidateInfo.candidateId}. */
	private String targetId;

	private String text;

	/** Display name captured at creation so readers never need VIEW_USERS to see who wrote it. */
	private String authorName;

	@CreatedBy
	private String authorEmail;

	@CreatedDate
	private Instant createdAt;

	@LastModifiedDate
	private Instant lastUpdatedAt;

	@LastModifiedBy
	private String lastUpdatedBy;
}
