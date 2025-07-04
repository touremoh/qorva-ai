package ai.qorva.core.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtDTO implements Serializable {
	@JsonProperty("token_type")
	private String tokenType;

	@JsonProperty("expires_in")
	private long expiresIn;

	@JsonProperty("ext_expires_in")
	private long extExpiresIn;

	@JsonProperty("access_token")
	private String accessToken;
}
