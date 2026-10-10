package ai.qorva.core.admin.security;

import ai.qorva.core.dto.QorvaErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Locale;

/** Writes the API's usual error body from a filter, where the controller advice cannot reach. The console is English-only. */
@Component
public class AdminErrorWriter {

	private final MessageSource messageSource;
	private final ObjectMapper mapper;

	public AdminErrorWriter(MessageSource messageSource, ObjectMapper objectMapper) {
		this.messageSource = messageSource;
		this.mapper = objectMapper.copy().registerModule(new JavaTimeModule());
	}

	public void write(HttpServletResponse response, HttpStatus status, String errorCode) throws IOException {
		var body = QorvaErrorResponse.builder()
			.errorCode(errorCode)
			.message(messageSource.getMessage(errorCode, null, errorCode, Locale.ENGLISH))
			.status(status)
			.code(status.value())
			.timestamp(LocalDateTime.now())
			.build();
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(mapper.writeValueAsString(body));
	}
}
