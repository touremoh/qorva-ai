package ai.qorva.core.exception;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

@Getter
@Setter
public class QorvaException extends Exception {
	private HttpStatus status;
	private Integer httpStatusCode;
	private String message;
	/** Optional MessageFormat args for parameterised message keys (e.g. "{0}" in properties files). */
	private Object[] params;

	public QorvaException(String messageKey) {
		super(messageKey);
	}

	/** Use for messages that require runtime arguments, e.g. a filename. */
	public QorvaException(String messageKey, Object... params) {
		super(messageKey);
		this.params = params;
	}

	public QorvaException(String messageKey, Integer httpStatusCode, HttpStatus status) {
		super(messageKey);
		this.httpStatusCode = httpStatusCode;
		this.message = messageKey;
		this.status = status;
	}

	public QorvaException(String messageKey, Integer httpStatusCode, HttpStatus status, Object... params) {
		super(messageKey);
		this.httpStatusCode = httpStatusCode;
		this.message = messageKey;
		this.status = status;
		this.params = params;
	}

	public QorvaException(String messageKey, Throwable cause) {
		super(messageKey, cause);
	}

	/** Use when a cause must be preserved AND the message key takes runtime arguments. */
	public QorvaException(String messageKey, Throwable cause, Object... params) {
		super(messageKey, cause);
		this.params = params;
	}

	public QorvaException(String messageKey, Throwable cause, Integer httpStatusCode, HttpStatus status) {
		super(messageKey, cause);
		this.httpStatusCode = httpStatusCode;
		this.message = messageKey;
		this.status = status;
	}
}
