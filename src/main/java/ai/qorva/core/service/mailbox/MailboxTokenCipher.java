package ai.qorva.core.service.mailbox;

import ai.qorva.core.config.MailboxProperties;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.SecretBlobCipher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** {@link MailboxTokens} ⇄ encrypted blob, keyed by {@code qorva.mailbox.credentials-key} (independent of the ATS key). */
@Component
public class MailboxTokenCipher {

	private final SecretBlobCipher cipher;
	private final ObjectMapper objectMapper;

	public MailboxTokenCipher(MailboxProperties properties, ObjectMapper objectMapper) {
		this.cipher = new SecretBlobCipher(properties.getCredentialsKey(), "qorva.mailbox.credentials-key");
		this.objectMapper = objectMapper;
	}

	public String encrypt(MailboxTokens tokens) throws QorvaException {
		try {
			return cipher.encrypt(objectMapper.writeValueAsBytes(tokens));
		} catch (Exception e) {
			throw new QorvaException(QorvaErrorCodes.HTTP_UNEXPECTED,
				HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	public MailboxTokens decrypt(String encryptedBlob) throws QorvaException {
		try {
			return objectMapper.readValue(cipher.decrypt(encryptedBlob), MailboxTokens.class);
		} catch (Exception e) {
			throw new QorvaException(QorvaErrorCodes.HTTP_UNEXPECTED,
				HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
