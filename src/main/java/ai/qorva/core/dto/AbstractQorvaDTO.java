package ai.qorva.core.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class AbstractQorvaDTO implements QorvaDTO {
	protected String id;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	protected String languageCode;
}
