package ai.qorva.core.service.mailbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Delegated OAuth tokens of a connected mailbox — only ever stored encrypted (see {@link MailboxTokenCipher}). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailboxTokens {
	private String accessToken;
	private String refreshToken;
	private Instant expiresAt;
	private String scope;
}
