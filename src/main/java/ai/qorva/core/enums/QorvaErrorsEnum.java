package ai.qorva.core.enums;

import ai.qorva.core.exception.QorvaErrorCodes;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum QorvaErrorsEnum {

	RESOURCE_NOT_FOUND("404", HttpStatus.NOT_FOUND, QorvaErrorCodes.HTTP_NOT_FOUND),
	BAD_REQUEST("400", HttpStatus.BAD_REQUEST, QorvaErrorCodes.HTTP_BAD_REQUEST),
	UNAUTHORIZED("401", HttpStatus.UNAUTHORIZED, QorvaErrorCodes.HTTP_UNAUTHORIZED),
	FORBIDDEN("403", HttpStatus.FORBIDDEN, QorvaErrorCodes.HTTP_FORBIDDEN),
	INTERNAL_SERVER_ERROR("500", HttpStatus.INTERNAL_SERVER_ERROR, QorvaErrorCodes.HTTP_UNEXPECTED),
	CONFLICT("409", HttpStatus.CONFLICT, QorvaErrorCodes.HTTP_CONFLICT),
	VALIDATION_ERROR("422", HttpStatus.UNPROCESSABLE_ENTITY, QorvaErrorCodes.HTTP_VALIDATION);

	private final String code;
	private final HttpStatus httpStatus;
	/** Message key — resolved via MessageSource in the exception handler. */
	private final String messageKey;

	QorvaErrorsEnum(String code, HttpStatus httpStatus, String messageKey) {
		this.code = code;
		this.httpStatus = httpStatus;
		this.messageKey = messageKey;
	}

	/** @deprecated Use {@link #getMessageKey()} and resolve via MessageSource. */
	@Deprecated
	public String getMessage() {
		return messageKey;
	}

	/**
	 * Utility method to get a QorvaErrorsEnum by its code.
	 *
	 * @param code the error code
	 * @return the matching QorvaErrorsEnum or null if no match is found
	 */
	public static QorvaErrorsEnum getByCode(String code) {
		for (QorvaErrorsEnum error : values()) {
			if (error.code.equals(code)) {
				return error;
			}
		}
		return null;
	}

	public static QorvaErrorsEnum getByCode(Integer code) {
		if (code == null) {
			return null;
		}
		return getByCode(String.valueOf(code));
	}
}
