package ai.qorva.core.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum QorvaErrorsEnum {

	RESOURCE_NOT_FOUND("404", HttpStatus.NOT_FOUND, "The requested resource was not found."),
	BAD_REQUEST("400", HttpStatus.BAD_REQUEST, "Invalid request parameters."),
	UNAUTHORIZED("401", HttpStatus.UNAUTHORIZED, "Unauthorized access to the resource."),
	FORBIDDEN("403", HttpStatus.FORBIDDEN, "Access to the resource is forbidden."),
	INTERNAL_SERVER_ERROR("500", HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred."),
	CONFLICT("409", HttpStatus.CONFLICT, "The request could not be processed due to a conflict."),
	VALIDATION_ERROR("422", HttpStatus.UNPROCESSABLE_ENTITY, "One or more fields failed validation.");

	private final String code;
	private final HttpStatus httpStatus;
	private final String message;

	QorvaErrorsEnum(String code, HttpStatus httpStatus, String message) {
		this.code = code;
		this.httpStatus = httpStatus;
		this.message = message;
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
