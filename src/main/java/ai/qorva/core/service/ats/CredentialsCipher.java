package ai.qorva.core.service.ats;

import ai.qorva.core.config.AtsProperties;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.SecretBlobCipher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM encryption for stored ATS credentials, keyed by {@code qorva.ats.credentials-key}.
 * The cipher itself lives in {@link SecretBlobCipher} (shared with the mailbox connections, each
 * domain with its own key); this class only adds the JSON (de)serialisation of {@link AtsCredentials}.
 * Losing the key invalidates every stored connection — tenants would simply reconnect.
 */
@Component
public class CredentialsCipher {

	private final SecretBlobCipher cipher;
	private final ObjectMapper objectMapper;

	public CredentialsCipher(AtsProperties properties, ObjectMapper objectMapper) {
		this.cipher = new SecretBlobCipher(properties.getCredentialsKey(), "qorva.ats.credentials-key");
		this.objectMapper = objectMapper;
	}

	public String encrypt(AtsCredentials credentials) throws QorvaException {
		try {
			return cipher.encrypt(objectMapper.writeValueAsBytes(credentials));
		} catch (Exception e) {
			throw new QorvaException(QorvaErrorCodes.HTTP_UNEXPECTED,
				HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	public AtsCredentials decrypt(String encryptedBlob) throws QorvaException {
		try {
			return objectMapper.readValue(cipher.decrypt(encryptedBlob), AtsCredentials.class);
		} catch (Exception e) {
			throw new QorvaException(QorvaErrorCodes.HTTP_UNEXPECTED,
				HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
