package ai.qorva.core.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Body of {@code POST /notes} (all fields) and {@code PUT /notes/{id}} (text only). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NoteRequest {
	private String targetType;
	private String targetId;
	private String text;
}
