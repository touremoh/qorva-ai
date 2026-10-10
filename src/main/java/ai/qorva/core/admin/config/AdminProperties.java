package ai.qorva.core.admin.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * The admin console's backend ({@code /admin/**}). Off by default: until {@code QORVA_ADMIN_ENABLED=true} every
 * admin route answers 404, so the code can ship to an environment before anyone uses it there.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "qorva.admin")
public class AdminProperties {

	private boolean enabled = false;

	/** Base64 HMAC key for admin tokens; must differ from the tenant {@code jwt.secret}. Required when enabled. */
	private String jwtSecret;

	/** Admin session length; the console refreshes while the admin is active. */
	private Duration sessionTtl = Duration.ofMinutes(30);

	/** Lifetime of the set-password link emailed to a new admin. */
	private Duration setPasswordTtl = Duration.ofHours(72);

	/** Where the console runs; set-password links point at {@code <consoleBaseUrl>/set-password?token=}. */
	private String consoleBaseUrl = "http://localhost:5175";

	/** Origins allowed to call {@code /admin/**} from a browser (none while the console runs behind the Vite proxy). */
	private List<String> allowedOrigins = new ArrayList<>();

	/** First admin (OWNER), created at start-up when there is no admin yet. */
	private String bootstrapEmail;

	/** How long a deleted tenant's data is kept before the purge. */
	private int purgeGraceMonths = 2;

	/** Testers whose access ends within this window show as "expiring soon". */
	private Duration expiringSoon = Duration.ofHours(48);

	private SecretKey secretKey;

	public SecretKey secretKey() {
		if (secretKey == null) {
			if (!StringUtils.hasText(jwtSecret)) {
				throw new IllegalStateException("qorva.admin.jwt-secret (ADMIN_JWT_SECRET) is required when the admin API is enabled");
			}
			secretKey = new SecretKeySpec(Base64.getDecoder().decode(jwtSecret), "HmacSHA512");
		}
		return secretKey;
	}
}
