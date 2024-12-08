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

	public QorvaException(String message) {
		super(message);
	}

	public QorvaException(String message, Integer httpStatusCode, HttpStatus status) {
		super(message);
		this.httpStatusCode = httpStatusCode;
		this.message = message;
		this.status = status;
	}

	public QorvaException(String message, Throwable cause) {
		super(message, cause);
	}

	public QorvaException(String message, Throwable cause, Integer httpStatusCode, HttpStatus status) {
		super(message, cause);
		this.httpStatusCode = httpStatusCode;
		this.message = message;
		this.status = status;
	}
}
