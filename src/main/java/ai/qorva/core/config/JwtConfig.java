package ai.qorva.core.config;

import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Getter
@Setter
@Configuration
public class JwtConfig {

	@Value("${jwt.timeToLiveInMillis}")
	private long timeToLiveInMillis;

	@Value("${jwt.secret}")
	private String b64Secret;

	private SecretKey secretKey;

	@PostConstruct
	void init() {
		byte[] keyBytes = Base64.getDecoder().decode(b64Secret);
		this.secretKey = new SecretKeySpec(keyBytes, SignatureAlgorithm.HS512.getJcaName());
	}
}
