package ai.qorva.core.exception;

import org.springframework.http.HttpStatus;

/**
 * The domain errors the API answers with, by HTTP status. {@code messageKey} is an error code from
 * {@link QorvaErrorCodes} (translated by the app) or, for legacy call sites, a plain message.
 */
public final class QorvaErrors {

	private QorvaErrors() {
	}

	public static QorvaException badRequest(String messageKey) {
		return of(messageKey, HttpStatus.BAD_REQUEST);
	}

	/** A 400 whose message key takes MessageFormat arguments ({0}, {1}…). */
	public static QorvaException badRequest(String messageKey, Object... params) {
		return new QorvaException(messageKey, HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST, params);
	}

	public static QorvaException unauthorized(String messageKey) {
		return of(messageKey, HttpStatus.UNAUTHORIZED);
	}

	public static QorvaException forbidden(String messageKey) {
		return of(messageKey, HttpStatus.FORBIDDEN);
	}

	public static QorvaException notFound(String messageKey) {
		return of(messageKey, HttpStatus.NOT_FOUND);
	}

	public static QorvaException conflict(String messageKey) {
		return of(messageKey, HttpStatus.CONFLICT);
	}

	public static QorvaException badRequest(String messageKey, Throwable cause) {
		return of(messageKey, cause, HttpStatus.BAD_REQUEST);
	}

	public static QorvaException unauthorized(String messageKey, Throwable cause) {
		return of(messageKey, cause, HttpStatus.UNAUTHORIZED);
	}

	public static QorvaException forbidden(String messageKey, Throwable cause) {
		return of(messageKey, cause, HttpStatus.FORBIDDEN);
	}

	public static QorvaException notFound(String messageKey, Throwable cause) {
		return of(messageKey, cause, HttpStatus.NOT_FOUND);
	}

	public static QorvaException conflict(String messageKey, Throwable cause) {
		return of(messageKey, cause, HttpStatus.CONFLICT);
	}

	public static QorvaException of(String messageKey, HttpStatus status) {
		return new QorvaException(messageKey, status.value(), status);
	}

	public static QorvaException of(String messageKey, Throwable cause, HttpStatus status) {
		return new QorvaException(messageKey, cause, status.value(), status);
	}
}
