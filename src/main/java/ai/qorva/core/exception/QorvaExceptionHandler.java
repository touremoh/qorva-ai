package ai.qorva.core.exception;

import ai.qorva.core.dto.QorvaErrorResponse;
import ai.qorva.core.enums.QorvaErrorsEnum;
import org.mapstruct.ap.internal.util.Strings;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.Objects;



@ControllerAdvice
public class QorvaExceptionHandler extends ResponseEntityExceptionHandler {

	/**
	 * Handles QorvaException and maps it to an appropriate error response based on QorvaErrorsEnum.
	 */
	@ExceptionHandler(value = {QorvaException.class})
	protected ResponseEntity<Object> handleQorvaException(QorvaException ex) {
		QorvaErrorsEnum errorEnum = QorvaErrorsEnum.getByCode(ex.getHttpStatusCode());

		var response = QorvaErrorResponse.builder()
			.message(
				StringUtils.hasText(ex.getMessage())
					? ex.getMessage()
					: (Objects.nonNull(errorEnum) ? errorEnum.getMessage() : "An unexpected error occurred.")
			)
			.status(Objects.nonNull(errorEnum) ? errorEnum.getHttpStatus() : HttpStatus.INTERNAL_SERVER_ERROR)
			.code(Objects.nonNull(errorEnum) ? Integer.parseInt(errorEnum.getCode()) : HttpStatus.INTERNAL_SERVER_ERROR.value())
			.timestamp(LocalDateTime.now())
			.build();

		return ResponseEntity.status(response.getStatus()).body(response);
	}

	/**
	 * Handles all generic exceptions not covered by other handlers.
	 */
	@ExceptionHandler(value = {Exception.class})
	protected ResponseEntity<Object> handleGenericException(Exception ex) {
		var response = QorvaErrorResponse.builder()
			.message("An unexpected error occurred: " + ex.getMessage())
			.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.code(HttpStatus.INTERNAL_SERVER_ERROR.value())
			.timestamp(LocalDateTime.now())
			.build();

		return ResponseEntity.status(response.getStatus()).body(response);
	}
}
