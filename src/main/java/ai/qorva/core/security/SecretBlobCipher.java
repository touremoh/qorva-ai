package ai.qorva.core.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM over an opaque byte blob, keyed by a configured string. The key string is hashed
 * (SHA-256) into the AES key, a random 12-byte IV prefixes each ciphertext, and the whole blob is
 * base64. One instance per secret domain (ATS credentials, mailbox tokens, …) so each key rotates
 * on its own — losing a key invalidates that domain's stored secrets only, and users reconnect.
 */
public class SecretBlobCipher {

	private static final int IV_LENGTH = 12;
	private static final int TAG_BITS = 128;

	private final SecretKeySpec key;
	private final SecureRandom random = new SecureRandom();

	public SecretBlobCipher(String keyString, String propertyName) {
		if (keyString == null || keyString.isBlank()) {
			throw new IllegalStateException(propertyName + " is not configured");
		}
		try {
			var digest = MessageDigest.getInstance("SHA-256").digest(keyString.getBytes(StandardCharsets.UTF_8));
			this.key = new SecretKeySpec(digest, "AES");
		} catch (Exception e) {
			throw new IllegalStateException("Could not derive key from " + propertyName, e);
		}
	}

	public String encrypt(byte[] plaintext) throws GeneralSecurityException {
		var iv = new byte[IV_LENGTH];
		random.nextBytes(iv);
		var cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
		var encrypted = cipher.doFinal(plaintext);
		var blob = new byte[IV_LENGTH + encrypted.length];
		System.arraycopy(iv, 0, blob, 0, IV_LENGTH);
		System.arraycopy(encrypted, 0, blob, IV_LENGTH, encrypted.length);
		return Base64.getEncoder().encodeToString(blob);
	}

	public byte[] decrypt(String encryptedBlob) throws GeneralSecurityException {
		var blob = Base64.getDecoder().decode(encryptedBlob);
		var cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, blob, 0, IV_LENGTH));
		return cipher.doFinal(blob, IV_LENGTH, blob.length - IV_LENGTH);
	}
}
