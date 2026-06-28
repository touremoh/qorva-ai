package ai.qorva.core.exception;

import ai.qorva.core.dto.QorvaErrorResponse;
import ai.qorva.core.enums.QorvaErrorsEnum;
import ai.qorva.core.security.LanguageContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@RestControllerAdvice(annotations = RestController.class)
public class QorvaExceptionHandler extends ResponseEntityExceptionHandler {

	@Autowired
	private MessageSource messageSource;

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
		log.error("Access denied", ex);
		Locale locale = resolveLocale(request);
		String message = messageSource.getMessage(QorvaErrorCodes.ACCESS_FORBIDDEN, null, locale);

		var response = QorvaErrorResponse.builder()
			.errorCode(QorvaErrorCodes.ACCESS_FORBIDDEN)
			.message(message)
			.status(HttpStatus.FORBIDDEN)
			.code(HttpStatus.FORBIDDEN.value())
			.timestamp(LocalDateTime.now())
			.build();

		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
	}

	@ExceptionHandler(value = {QorvaException.class})
	protected ResponseEntity<Object> handleQorvaException(QorvaException ex, HttpServletRequest request) {
		log.error("QorvaException: {}", ex.getMessage(), ex);

		Locale locale = resolveLocale(request);
		QorvaErrorsEnum errorEnum = QorvaErrorsEnum.getByCode(ex.getHttpStatusCode());

		String errorCode = StringUtils.hasText(ex.getMessage())
			? ex.getMessage()
			: (Objects.nonNull(errorEnum) ? errorEnum.getMessageKey() : QorvaErrorCodes.HTTP_UNEXPECTED);

		String translated = messageSource.getMessage(errorCode, ex.getParams(), locale);

		HttpStatus httpStatus = Objects.nonNull(errorEnum) ? errorEnum.getHttpStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
		int code = Objects.nonNull(errorEnum) ? Integer.parseInt(errorEnum.getCode()) : HttpStatus.INTERNAL_SERVER_ERROR.value();

		var response = QorvaErrorResponse.builder()
			.errorCode(errorCode)
			.message(translated)
			.status(httpStatus)
			.code(code)
			.timestamp(LocalDateTime.now())
			.build();

		return ResponseEntity.status(httpStatus).body(response);
	}

	@ExceptionHandler(value = {Exception.class})
	protected ResponseEntity<Object> handleGenericException(Exception ex, HttpServletRequest request) {
		log.error("Unhandled exception: {}", ex.getMessage(), ex);

		Locale locale = resolveLocale(request);
		String message = messageSource.getMessage(QorvaErrorCodes.HTTP_UNEXPECTED, null, locale);

		var response = QorvaErrorResponse.builder()
			.errorCode(QorvaErrorCodes.HTTP_UNEXPECTED)
			.message(message)
			.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.code(HttpStatus.INTERNAL_SERVER_ERROR.value())
			.timestamp(LocalDateTime.now())
			.build();

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
	}

	/**
	 * Resolves the request locale from Accept-Language header, falling back to the
	 * thread-local LanguageContextHolder (populated by controllers on write operations).
	 */
	private Locale resolveLocale(HttpServletRequest request) {
		String lang = request != null ? request.getHeader("Accept-Language") : null;
		if (!StringUtils.hasText(lang)) {
			lang = LanguageContextHolder.getLanguage();
		}
		try {
			String tag = lang.split(",")[0].trim().split(";")[0].trim();
			return Locale.forLanguageTag(tag);
		} catch (Exception e) {
			return Locale.ENGLISH;
		}
	}
}
