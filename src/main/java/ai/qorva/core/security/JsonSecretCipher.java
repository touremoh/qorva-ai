package ai.qorva.core.security;

import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;

/**
 * Stores a value as an encrypted JSON blob ({@link SecretBlobCipher}, AES-GCM). Subclasses only name
 * the value type and the key; a failure never reveals why (500, generic code).
 */
public abstract class JsonSecretCipher<T> {

	private final SecretBlobCipher cipher;
	private final ObjectMapper objectMapper;
	private final Class<T> type;

	protected JsonSecretCipher(String key, String keyProperty, Class<T> type, ObjectMapper objectMapper) {
		this.cipher = new SecretBlobCipher(key, keyProperty);
		this.objectMapper = objectMapper;
		this.type = type;
	}

	public String encrypt(T value) throws QorvaException {
		try {
			return cipher.encrypt(objectMapper.writeValueAsBytes(value));
		} catch (Exception e) {
			throw failure();
		}
	}

	public T decrypt(String encryptedBlob) throws QorvaException {
		try {
			return objectMapper.readValue(cipher.decrypt(encryptedBlob), type);
		} catch (Exception e) {
			throw failure();
		}
	}

	private static QorvaException failure() {
		return new QorvaException(QorvaErrorCodes.HTTP_UNEXPECTED,
			HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR);
	}
}
