package ai.qorva.core.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountRegistrationDTO {

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String firstName;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String lastName;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String email;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String password;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String companyName;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String languageCode;
}
