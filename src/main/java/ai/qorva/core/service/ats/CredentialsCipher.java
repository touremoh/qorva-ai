package ai.qorva.core.service.ats;

import ai.qorva.core.config.AtsProperties;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for stored ATS credentials. The configured key string is
 * hashed (SHA-256) into the AES key, a random 12-byte IV prefixes each ciphertext,
 * and the whole blob is base64. Losing the key invalidates every stored connection —
 * tenants would simply reconnect.
 */
@Component
public class CredentialsCipher {

	private static final int IV_LENGTH = 12;
	private static final int TAG_BITS = 128;

	private final SecretKeySpec key;
	private final ObjectMapper objectMapper;
	private final SecureRandom random = new SecureRandom();

	public CredentialsCipher(AtsProperties properties, ObjectMapper objectMapper) {
		if (!StringUtils.hasText(properties.getCredentialsKey())) {
			throw new IllegalStateException("qorva.ats.credentials-key is not configured");
		}
		try {
			var digest = MessageDigest.getInstance("SHA-256")
				.digest(properties.getCredentialsKey().getBytes(StandardCharsets.UTF_8));
			this.key = new SecretKeySpec(digest, "AES");
		} catch (Exception e) {
			throw new IllegalStateException("Could not derive ATS credentials key", e);
		}
		this.objectMapper = objectMapper;
	}

	public String encrypt(AtsCredentials credentials) throws QorvaException {
		try {
			var plaintext = objectMapper.writeValueAsBytes(credentials);
			var iv = new byte[IV_LENGTH];
			random.nextBytes(iv);
			var cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			var encrypted = cipher.doFinal(plaintext);
			var blob = new byte[IV_LENGTH + encrypted.length];
			System.arraycopy(iv, 0, blob, 0, IV_LENGTH);
			System.arraycopy(encrypted, 0, blob, IV_LENGTH, encrypted.length);
			return Base64.getEncoder().encodeToString(blob);
		} catch (Exception e) {
			throw new QorvaException(QorvaErrorCodes.HTTP_UNEXPECTED,
				HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	public AtsCredentials decrypt(String encryptedBlob) throws QorvaException {
		try {
			var blob = Base64.getDecoder().decode(encryptedBlob);
			var cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, blob, 0, IV_LENGTH));
			var plaintext = cipher.doFinal(blob, IV_LENGTH, blob.length - IV_LENGTH);
			return objectMapper.readValue(plaintext, AtsCredentials.class);
		} catch (Exception e) {
			throw new QorvaException(QorvaErrorCodes.HTTP_UNEXPECTED,
				HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
