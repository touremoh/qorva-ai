package ai.qorva.core.mapper;


import ai.qorva.core.dto.JwtDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwtMapper {
	private final ObjectMapper om;

	public JwtMapper(ObjectMapper om) {
		this.om = om;
	}

	public JwtDTO map(String token) throws JsonProcessingException {
		return om.readValue(token, JwtDTO.class);
	}

	public void merge(JwtDTO target, JwtDTO source) {
		target.setAccessToken(source.getAccessToken());
		target.setExpiresIn(source.getExpiresIn());
		target.setTokenType(source.getTokenType());
		target.setExtExpiresIn(source.getExtExpiresIn());
	}
}
