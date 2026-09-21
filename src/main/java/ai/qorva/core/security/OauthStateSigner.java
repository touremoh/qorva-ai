package ai.qorva.core.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * HMAC-signed OAuth {@code state}: {@code base64url(field1|field2|…) + "." + hmacHex}. The public
 * callback trusts nothing but this signature — which fields ride in it (tenant, user, provider,
 * expiry, nonce) is the caller's contract, and the caller also checks the expiry it put there.
 */
public class OauthStateSigner {

	private final String secret;

	public OauthStateSigner(String secret) {
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException("OAuth state signing key is not configured");
		}
		this.secret = secret;
	}

	public String sign(List<String> fields) {
		var payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
			String.join("|", fields.stream().map(f -> f != null ? f : "").toList())
				.getBytes(StandardCharsets.UTF_8));
		return payload + "." + hmacHex(payload.getBytes(StandardCharsets.UTF_8), secret);
	}

	/** Signature-checked fields, in the order they were signed. Throws on any tamper. */
	public String[] verify(String state) {
		int dot = state.lastIndexOf('.');
		if (dot <= 0) {
			throw new IllegalStateException("malformed state");
		}
		var payload = state.substring(0, dot);
		var signature = state.substring(dot + 1);
		var expected = hmacHex(payload.getBytes(StandardCharsets.UTF_8), secret);
		if (!matches(expected, signature)) {
			throw new IllegalStateException("bad signature");
		}
		var decoded = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
		return decoded.split("\\|", -1);
	}

	public static String hmacHex(byte[] body, String secret) {
		try {
			var mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(body));
		} catch (Exception e) {
			throw new IllegalStateException("HMAC failure", e);
		}
	}

	public static boolean matches(String expectedHex, String providedHex) {
		if (expectedHex == null || providedHex == null) {
			return false;
		}
		return MessageDigest.isEqual(
			expectedHex.toLowerCase().getBytes(StandardCharsets.UTF_8),
			providedHex.toLowerCase().getBytes(StandardCharsets.UTF_8));
	}
}
