package ai.qorva.core.config;

import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {

	private long timeToLiveInMillis = 3600000L;

	/** TTL for single-use set-password tokens. Default 72h. */
	private long setPasswordTtlInMillis = 259200000L;

	private String secret; // b64 Secret key

	private SecretKey secretKey;

	@PostConstruct
	void init() {
		byte[] keyBytes = Base64.getDecoder().decode(secret);
		this.secretKey = new SecretKeySpec(keyBytes, SignatureAlgorithm.HS512.getJcaName());
	}
}
