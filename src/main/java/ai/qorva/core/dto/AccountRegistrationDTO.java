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
	private String organizationName;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String organizationSize;

	/** Recruitment segment ("What type of recruitment do you mostly do?") — see RecruitmentTypeEnum. */
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String recruitmentType;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String languageCode;
}
