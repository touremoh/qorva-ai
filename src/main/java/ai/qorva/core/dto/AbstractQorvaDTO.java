package ai.qorva.core.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public abstract class AbstractQorvaDTO implements QorvaDTO {
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	protected String languageCode;
}
