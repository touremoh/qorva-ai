package ai.qorva.core.service.ats;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** HMAC-SHA256 webhook signature checks, constant-time compared. */
public final class AtsWebhookVerifier {

	private AtsWebhookVerifier() {}

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
