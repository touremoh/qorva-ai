package ai.qorva.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Email MFA limits. A 6-digit code is only as strong as the attempt limit around it, so the
 * counters live on the Mongo challenge (shared by every App Runner instance), not in memory.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "qorva.mfa")
public class MfaProperties {

	/** Digits in an emailed code. */
	private int codeLength = 6;

	/** How long a challenge (and the code it carries) stays usable. */
	private Duration codeTtl = Duration.ofMinutes(10);

	/** Wrong codes accepted per challenge before it is burnt and the user must start over. */
	private int maxAttempts = 5;

	/** Minimum gap between two sends on the same challenge. */
	private Duration resendCooldown = Duration.ofSeconds(60);

	/** Emails sent per challenge, the first one included. */
	private int maxSendsPerChallenge = 5;

	/** Challenges a user may open inside {@link #challengeWindow} — caps mail-bombing by a password holder. */
	private int maxChallengesPerWindow = 10;

	private Duration challengeWindow = Duration.ofMinutes(15);
}
